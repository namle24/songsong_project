"""
SongSong Benchmark Chart Generator
Reads benchmark_results.csv and generates performance charts.

Usage: python plot_benchmark.py
"""
import csv
import matplotlib.pyplot as plt
import matplotlib
matplotlib.use('Agg')  # Non-interactive backend for saving to file

CSV_FILE = "benchmark_results.csv"
OUTPUT_PNG = "benchmark_chart.png"

def main():
    # Read CSV
    daemons, times, speeds, speedups = [], [], [], []
    with open(CSV_FILE, "r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            daemons.append(int(row["daemons"]))
            times.append(int(row["duration_ms"]))
            speeds.append(float(row["speed_mbps"]))
            speedups.append(float(row["speedup"]))

    # --- Create figure with 2 subplots ---
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))
    fig.suptitle("SongSong Parallel Download — Benchmark Results", fontsize=16, fontweight="bold")
    fig.patch.set_facecolor("#f8fafc")

    # === Chart 1: Download Time & Speed vs Daemons ===
    color_time = "#e11d48"
    color_speed = "#0891b2"

    ax1.set_facecolor("#f1f5f9")
    ln1 = ax1.plot(daemons, times, "o-", color=color_time, linewidth=2.5, markersize=10, label="Download Time (ms)")
    ax1.set_xlabel("Number of Daemons (Sources)", fontsize=12)
    ax1.set_ylabel("Download Time (ms)", fontsize=12, color=color_time)
    ax1.tick_params(axis="y", labelcolor=color_time)
    ax1.set_xticks(daemons)
    ax1.grid(True, alpha=0.3)
    ax1.set_title("Performance vs Number of Sources", fontsize=13, fontweight="bold")

    # Add time labels
    for d, t in zip(daemons, times):
        ax1.annotate(f"{t:,} ms", (d, t), textcoords="offset points", xytext=(0, 15),
                     ha="center", fontsize=9, color=color_time, fontweight="bold")

    # Secondary Y-axis for speed
    ax1b = ax1.twinx()
    ln2 = ax1b.plot(daemons, speeds, "s--", color=color_speed, linewidth=2, markersize=8, label="Speed (MB/s)")
    ax1b.set_ylabel("Speed (MB/s)", fontsize=12, color=color_speed)
    ax1b.tick_params(axis="y", labelcolor=color_speed)

    # Combined legend
    lns = ln1 + ln2
    labs = [l.get_label() for l in lns]
    ax1.legend(lns, labs, loc="center right", fontsize=10)

    # === Chart 2: Speedup Bar Chart ===
    colors = ["#94a3b8", "#06b6d4", "#8b5cf6", "#10b981"]
    bars = ax2.bar(daemons, speedups, color=colors[:len(daemons)], width=0.6, edgecolor="white", linewidth=1.5)
    ax2.set_facecolor("#f1f5f9")
    ax2.set_xlabel("Number of Daemons (Sources)", fontsize=12)
    ax2.set_ylabel("Speedup Factor (x)", fontsize=12)
    ax2.set_xticks(daemons)
    ax2.set_title("Speedup: Parallel vs Sequential", fontsize=13, fontweight="bold")
    ax2.set_ylim(0, max(speedups) * 1.3)
    ax2.grid(True, axis="y", alpha=0.3)

    # Ideal linear speedup reference line
    ax2.plot(daemons, daemons, "k--", alpha=0.4, linewidth=1.5, label="Ideal Linear Speedup")
    ax2.legend(fontsize=10)

    # Add value labels on bars
    for bar, s in zip(bars, speedups):
        ax2.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 0.1,
                 f"{s:.2f}x", ha="center", fontsize=11, fontweight="bold")

    plt.tight_layout(rect=[0, 0, 1, 0.93])
    plt.savefig(OUTPUT_PNG, dpi=150, bbox_inches="tight")
    print(f"Chart saved to: {OUTPUT_PNG}")
    plt.show()

if __name__ == "__main__":
    main()
