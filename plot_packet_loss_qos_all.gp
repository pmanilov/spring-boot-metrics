set datafile separator ","
set terminal pngcairo size 1800,1500 enhanced font "Sans,11"
set grid
set key top left box width 2 spacing 1.2
set border linewidth 1.2
set tics out

csv = "experiment_results_packet_loss_qos_all.csv"
outdir = "result_packet_loss_qos_all"

clients_values = "1 2 5 10 15"
overhead_values = "0 2048 4096 8192 16384"
qos_values = "0 1 2"

# CSV columns:
# 1 Protocol
# 2 QoS
# 3 Clients
# 4 Overhead(bytes)
# 5 IntensityPerClient(req/sec)
# 6 TargetAggregateRate(req/sec)
# 7 ActualSendRate(req/sec)
# 8 ActualReceiveRate(req/sec)
# 9 Sent
# 10 Received
# 11 LossRatio
# 12 AvgDelay(ms)
# 13 AvgPacketSize(bytes)

system(sprintf("mkdir -p %s", outdir))

do for [clients in clients_values] {
    do for [overhead in overhead_values] {
        count = system(sprintf("awk -F, 'NR>1 && $3==%s && $4==%s' %s | wc -l", clients, overhead, csv)) + 0
        if (count == 0) { continue }

        outfile = sprintf("%s/result_packet_loss_qos_all_c%s_o%s.png", outdir, clients, overhead)
        set output outfile
        set multiplot layout 3,3 rowsfirst \
            title sprintf("Packet loss experiment: clients=%s, payload=%s bytes", clients, overhead) font ",16"

        do for [qos in qos_values] {
            cmd_mqtt = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT\"' %s", \
                clients, overhead, qos, csv)
            cmd_quic = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT-QUIC\"' %s", \
                clients, overhead, qos, csv)
            count_qos = system(sprintf("awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s' %s | wc -l", \
                clients, overhead, qos, csv)) + 0

            set title sprintf("Avg delay, QoS %s", qos)
            set xlabel "Input intensity (req/s)"
            set ylabel "Delay (ms)"
            set format y "%g"

            if (count_qos == 0) {
                set xrange [0:1]
                set yrange [0:1]
                plot NaN notitle
            } else {
                set xrange [0:*]
                set yrange [*:*]
                plot cmd_mqtt using 6:12 with linespoints lw 2 pt 7 ps 1.1 lc rgb "#d62728" title "MQTT", \
                     cmd_quic using 6:12 with linespoints lw 2 pt 5 ps 1.1 lc rgb "#1f77b4" title "MQTT-QUIC"
            }
        }

        do for [qos in qos_values] {
            cmd_mqtt = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT\"' %s", \
                clients, overhead, qos, csv)
            cmd_quic = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT-QUIC\"' %s", \
                clients, overhead, qos, csv)
            count_qos = system(sprintf("awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s' %s | wc -l", \
                clients, overhead, qos, csv)) + 0

            set title sprintf("Loss ratio, QoS %s", qos)
            set xlabel "Input intensity (req/s)"
            set ylabel "Loss ratio"
            set format y "%.2f"

            if (count_qos == 0) {
                set xrange [0:1]
                set yrange [0:1]
                plot NaN notitle
            } else {
                set xrange [0:*]
                set yrange [0:1]
                plot cmd_mqtt using 6:11 with linespoints lw 2 pt 7 ps 1.1 lc rgb "#d62728" title "MQTT", \
                     cmd_quic using 6:11 with linespoints lw 2 pt 5 ps 1.1 lc rgb "#1f77b4" title "MQTT-QUIC"
            }
        }

        do for [qos in qos_values] {
            cmd_mqtt = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT\"' %s", \
                clients, overhead, qos, csv)
            cmd_quic = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT-QUIC\"' %s", \
                clients, overhead, qos, csv)
            count_qos = system(sprintf("awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s' %s | wc -l", \
                clients, overhead, qos, csv)) + 0

            set title sprintf("Output intensity, QoS %s", qos)
            set xlabel "Input intensity (req/s)"
            set ylabel "Output intensity (msg/s)"
            set format y "%g"

            if (count_qos == 0) {
                set xrange [0:1]
                set yrange [0:1]
                plot NaN notitle
            } else {
                set xrange [0:*]
                set yrange [0:*]
                plot cmd_mqtt using 6:8 with linespoints lw 2 pt 7 ps 1.1 lc rgb "#d62728" title "MQTT", \
                     cmd_quic using 6:8 with linespoints lw 2 pt 5 ps 1.1 lc rgb "#1f77b4" title "MQTT-QUIC"
            }
        }

        unset multiplot
        unset output
        print sprintf("Saved %s", outfile)
    }
}
