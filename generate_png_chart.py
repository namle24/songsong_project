import csv
import matplotlib.pyplot as plt

def main():
    daemons = []
    speeds = []
    times = []

    try:
        with open('benchmark_results.csv', 'r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                daemons.append(int(row['daemons']))
                speeds.append(float(row['speed_mbps']))
                times.append(float(row['duration_ms']) / 1000)
    except Exception as e:
        print(f"Error reading CSV: {e}")
        return

    if not daemons:
        print("No data to plot.")
        return

    fig, ax1 = plt.subplots(figsize=(10, 6))

    color1 = '#1f77b4'
    ax1.set_xlabel('Number of Daemons (Parallel Connections)', fontsize=12, fontweight='bold')
    ax1.set_ylabel('Download Speed (MB/s)', color=color1, fontsize=12, fontweight='bold')
    line1 = ax1.plot(daemons, speeds, marker='o', color=color1, linewidth=2.5, markersize=8, label='Speed (MB/s)')
    ax1.tick_params(axis='y', labelcolor=color1)
    ax1.set_xticks(daemons)
    ax1.grid(True, linestyle='--', alpha=0.7)

    color2 = '#d62728'
    ax2 = ax1.twinx()
    ax2.set_ylabel('Download Time (seconds)', color=color2, fontsize=12, fontweight='bold')
    line2 = ax2.plot(daemons, times, marker='s', color=color2, linewidth=2.5, linestyle='--', markersize=8, label='Time (s)')
    ax2.tick_params(axis='y', labelcolor=color2)

    # Add legends
    lines = line1 + line2
    labels = [l.get_label() for l in lines]
    ax1.legend(lines, labels, loc='upper center', bbox_to_anchor=(0.5, -0.15), fancybox=True, shadow=True, ncol=2)

    plt.title('SongSong Download Performance', fontsize=16, fontweight='bold', pad=15)
    fig.tight_layout()
    
    plt.savefig('benchmark_chart.png', dpi=300, bbox_inches='tight')
    print("Successfully generated benchmark_chart.png")

if __name__ == '__main__':
    main()
