package edu.usth.songsong.benchmark;

import edu.usth.songsong.common.DirectoryService;
import edu.usth.songsong.daemon.FileTransferTask;
import edu.usth.songsong.directory.DirectoryServer;
import edu.usth.songsong.download.DownloadManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Automated benchmark tool for SongSong Parallel Download.
 * Measures download performance with 1..N daemons and generates
 * CSV results + HTML chart for the report.
 *
 * Usage: java -Dsongsong.network.delay=5 PerformanceBenchmark [fileSizeMB] [maxDaemons]
 */
public class PerformanceBenchmark {

    private static final int DEFAULT_FILE_SIZE_MB = 20;
    private static final int DEFAULT_MAX_DAEMONS = 4;
    private static final int BASE_PORT = 6001;
    private static final String DATA_DIR = "./benchmark_data";
    private static final String TEST_FILE = "benchmark_test.bin";
    private static final String CSV_OUT = "benchmark_results.csv";
    private static final String HTML_OUT = "benchmark_chart.html";

    private static final List<ServerSocket> servers = new ArrayList<>();
    private static final List<Thread> threads = new ArrayList<>();
    private static final List<ExecutorService> pools = new ArrayList<>();

    record Result(int daemons, int sizeMB, long ms, double mbps) {
        double speedup(Result base) { return (double) base.ms / this.ms; }
    }

    public static void main(String[] args) throws Exception {
        Logger.getLogger("edu.usth.songsong").setLevel(Level.WARNING);

        int sizeMB = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_FILE_SIZE_MB;
        int maxD = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_MAX_DAEMONS;
        int delay = Integer.getInteger("songsong.network.delay", 0);

        System.out.println("╔═══════════════════════════════════════════╗");
        System.out.println("║   SongSong Performance Benchmark Tool     ║");
        System.out.println("╚═══════════════════════════════════════════╝");
        System.out.printf("  File: %d MB | Max daemons: %d | Delay: %d ms/buffer%n%n", sizeMB, maxD, delay);

        new File(DATA_DIR).mkdirs();
        new File("./downloads").mkdirs();

        // 1. Generate test file
        System.out.println("[1/5] Generating test file...");
        genTestFile(sizeMB * 1024 * 1024);

        // 2. Start Directory
        System.out.println("[2/5] Starting Directory...");
        DirectoryServer dir = new DirectoryServer();
        Registry reg;
        try { reg = LocateRegistry.createRegistry(1099); }
        catch (Exception e) { reg = LocateRegistry.getRegistry("localhost", 1099); }
        reg.rebind("DirectoryService", dir);
        System.out.println("  ✓ Directory on port 1099");

        // 3. Benchmark
        System.out.println("[3/5] Running benchmarks...");
        List<Result> results = new ArrayList<>();

        for (int n = 1; n <= maxD; n++) {
            System.out.printf("%n  ▶ %d daemon(s)...", n);
            startDaemons(n);
            Thread.sleep(2000);

            new File("./downloads/" + TEST_FILE).delete();
            long t0 = System.nanoTime();
            new DownloadManager("localhost", 1099).downloadFile(TEST_FILE);
            long elapsed = (System.nanoTime() - t0) / 1_000_000;

            double speed = (sizeMB * 1000.0) / elapsed;
            results.add(new Result(n, sizeMB, elapsed, speed));
            System.out.printf(" %,d ms (%.2f MB/s)%n", elapsed, speed);

            stopDaemons();
            new File("./downloads/" + TEST_FILE).delete();
            Thread.sleep(1000);
        }

        // 4. CSV
        System.out.println("\n[4/5] Writing CSV...");
        writeCSV(results);
        System.out.println("  ✓ " + CSV_OUT);

        // 5. Chart
        System.out.println("[5/5] Generating chart...");
        genChart(results, delay);
        System.out.println("  ✓ " + HTML_OUT);

        printSummary(results);
        System.exit(0);
    }

    // ===== Test File =====
    private static void genTestFile(int size) throws IOException {
        File f = new File(DATA_DIR, TEST_FILE);
        if (f.exists() && f.length() == size) {
            System.out.printf("  ✓ Already exists (%d MB)%n", size / 1024 / 1024);
            return;
        }
        System.out.printf("  Creating %d MB...", size / 1024 / 1024);
        try (var fos = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            Random rng = new Random(42);
            int rem = size;
            while (rem > 0) {
                rng.nextBytes(buf);
                int w = Math.min(buf.length, rem);
                fos.write(buf, 0, w);
                rem -= w;
            }
        }
        System.out.println(" done");
    }

    // ===== Daemon Management =====
    private static void startDaemons(int count) throws Exception {
        DirectoryService ds = (DirectoryService) LocateRegistry
                .getRegistry("localhost", 1099).lookup("DirectoryService");
        File dataDir = new File(DATA_DIR);
        List<String> files = new ArrayList<>();
        for (File f : Objects.requireNonNull(dataDir.listFiles())) {
            if (f.isFile()) files.add(f.getName());
        }
        for (int i = 0; i < count; i++) {
            int port = BASE_PORT + i;
            ds.register("localhost", port, files);
            ServerSocket ss = new ServerSocket(port);
            servers.add(ss);
            ExecutorService pool = Executors.newFixedThreadPool(5);
            pools.add(pool);
            Thread t = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket c = ss.accept();
                        pool.submit(new FileTransferTask(c, dataDir));
                    } catch (IOException e) { break; }
                }
            }, "Daemon-" + port);
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
    }

    private static void stopDaemons() {
        for (ServerSocket ss : servers) try { ss.close(); } catch (IOException ignored) {}
        for (Thread t : threads) t.interrupt();
        for (ExecutorService p : pools) p.shutdownNow();
        try {
            DirectoryService ds = (DirectoryService) LocateRegistry
                    .getRegistry("localhost", 1099).lookup("DirectoryService");
            for (int i = 0; i < servers.size(); i++)
                try { ds.unregister("localhost", BASE_PORT + i); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        servers.clear(); threads.clear(); pools.clear();
    }

    // ===== CSV =====
    private static void writeCSV(List<Result> results) throws IOException {
        Result base = results.get(0);
        try (var pw = new PrintWriter(new FileWriter(CSV_OUT))) {
            pw.println("daemons,mode,file_size_mb,duration_ms,speed_mbps,speedup");
            for (Result r : results) {
                pw.printf("%d,%s,%d,%d,%.2f,%.2f%n", r.daemons,
                    r.daemons == 1 ? "sequential" : "parallel",
                    r.sizeMB, r.ms, r.mbps, r.speedup(base));
            }
        }
    }

    // ===== Summary =====
    private static void printSummary(List<Result> results) {
        Result base = results.get(0);
        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║             Benchmark Summary                 ║");
        System.out.println("╠═══════════════════════════════════════════════╣");
        System.out.printf("║ %-8s │ %-10s │ %-10s │ %-7s  ║%n", "Daemons", "Time(ms)", "Speed", "Speedup");
        System.out.println("╠═══════════════════════════════════════════════╣");
        for (Result r : results)
            System.out.printf("║ %-8d │ %,10d │ %7.2f MB/s│ %5.2fx   ║%n",
                    r.daemons, r.ms, r.mbps, r.speedup(base));
        System.out.println("╚═══════════════════════════════════════════════╝");
    }

    // ===== HTML Chart =====
    private static void genChart(List<Result> results, int delay) throws IOException {
        Result base = results.get(0);
        StringBuilder lbls = new StringBuilder("["), tms = new StringBuilder("["),
                spds = new StringBuilder("["), spups = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            if (i > 0) { lbls.append(","); tms.append(","); spds.append(","); spups.append(","); }
            lbls.append(r.daemons); tms.append(r.ms);
            spds.append(String.format("%.2f", r.mbps));
            spups.append(String.format("%.2f", r.speedup(base)));
        }
        lbls.append("]"); tms.append("]"); spds.append("]"); spups.append("]");
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String rows = buildRows(results, base);

        String html = HTML.replace("{{DATE}}", date).replace("{{SIZE}}", String.valueOf(results.get(0).sizeMB))
                .replace("{{DELAY}}", String.valueOf(delay)).replace("{{LABELS}}", lbls.toString())
                .replace("{{TIMES}}", tms.toString()).replace("{{SPEEDS}}", spds.toString())
                .replace("{{SPEEDUPS}}", spups.toString()).replace("{{ROWS}}", rows);
        try (var pw = new PrintWriter(new FileWriter(HTML_OUT))) { pw.print(html); }
    }

    private static String buildRows(List<Result> results, Result base) {
        StringBuilder sb = new StringBuilder();
        for (Result r : results)
            sb.append(String.format("<tr><td>%d</td><td>%s</td><td>%,d</td><td>%.2f</td><td>%.2fx</td></tr>",
                    r.daemons, r.daemons == 1 ? "Sequential" : "Parallel", r.ms, r.mbps, r.speedup(base)));
        return sb.toString();
    }

    private static final String HTML = """
<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
<title>SongSong Benchmark</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Segoe UI',system-ui,sans-serif;background:#0f172a;color:#e2e8f0;padding:30px}
h1{text-align:center;margin-bottom:5px;color:#38bdf8;font-size:28px}
.sub{text-align:center;color:#94a3b8;margin-bottom:30px;font-size:14px}
.charts{display:flex;gap:20px;margin-bottom:30px;flex-wrap:wrap}
.card{flex:1;min-width:380px;background:#1e293b;border-radius:16px;padding:20px;border:1px solid #334155}
table{width:100%;border-collapse:collapse;background:#1e293b;border-radius:12px;overflow:hidden}
th{background:#0ea5e9;color:#fff;padding:12px;font-weight:600}
td{padding:10px;text-align:center;border-bottom:1px solid #334155}
tr:hover{background:#334155}
.foot{text-align:center;margin-top:20px;color:#64748b;font-size:12px}
</style></head><body>
<h1>📊 SongSong Parallel Download — Benchmark Results</h1>
<p class="sub">File: {{SIZE}} MB | Simulated delay: {{DELAY}} ms/buffer | {{DATE}}</p>
<div class="charts">
<div class="card"><canvas id="c1"></canvas></div>
<div class="card"><canvas id="c2"></canvas></div>
</div>
<table><tr><th>Daemons</th><th>Mode</th><th>Time (ms)</th><th>Speed (MB/s)</th><th>Speedup</th></tr>
{{ROWS}}</table>
<p class="foot">Generated by SongSong PerformanceBenchmark</p>
<script>
const L={{LABELS}},T={{TIMES}},S={{SPEEDS}},U={{SPEEDUPS}};
const cfg={responsive:true,plugins:{legend:{labels:{color:'#e2e8f0'}}}};
new Chart(document.getElementById('c1'),{type:'line',data:{labels:L,datasets:[
{label:'Download Time (ms)',data:T,borderColor:'#f43f5e',backgroundColor:'rgba(244,63,94,0.2)',tension:0.3,fill:true,pointRadius:6},
{label:'Speed (MB/s)',data:S,borderColor:'#22d3ee',backgroundColor:'rgba(34,211,238,0.2)',tension:0.3,fill:true,yAxisID:'y1',pointRadius:6}
]},options:{...cfg,scales:{x:{title:{display:true,text:'Number of Daemons',color:'#94a3b8'},ticks:{color:'#94a3b8'}},
y:{title:{display:true,text:'Time (ms)',color:'#94a3b8'},ticks:{color:'#94a3b8'}},
y1:{position:'right',title:{display:true,text:'Speed (MB/s)',color:'#94a3b8'},ticks:{color:'#94a3b8'},grid:{drawOnChartArea:false}}
},plugins:{...cfg.plugins,title:{display:true,text:'Download Performance vs Number of Sources',color:'#e2e8f0',font:{size:16}}}}});
new Chart(document.getElementById('c2'),{type:'bar',data:{labels:L,datasets:[
{label:'Speedup (x)',data:U,backgroundColor:['#64748b','#22d3ee','#a78bfa','#34d399'],borderRadius:8}
]},options:{...cfg,scales:{x:{title:{display:true,text:'Number of Daemons',color:'#94a3b8'},ticks:{color:'#94a3b8'}},
y:{title:{display:true,text:'Speedup Factor',color:'#94a3b8'},ticks:{color:'#94a3b8'},beginAtZero:true}
},plugins:{...cfg.plugins,title:{display:true,text:'Speedup: Parallel vs Sequential',color:'#e2e8f0',font:{size:16}}}}});
</script></body></html>""";
}
