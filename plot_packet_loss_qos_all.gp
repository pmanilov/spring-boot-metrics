set datafile separator ","
set terminal pngcairo size 1800,1000 enhanced font "Sans,11"
set grid
set key top left box width 2 spacing 1.2
set border linewidth 1.2
set tics out

csv = "experiment_results_packet_loss_qos_all.csv"
outdir = "result_packet_loss_qos_all"

clients_values = "1 2 5 10"
overhead_values = "0 2048 4096 8192"
qos_values = "0 1 2"

# CSV columns:
# 1 Protocol
# 2 QoS
# 3 Clients
# 4 Overhead(bytes)
# 5 Intensity(req/sec)
# 6 Sent
# 7 Received
# 8 LossRatio
# 9 AvgDelay(ms)
# 10 AvgPacketSize(bytes)

system(sprintf("mkdir -p %s", outdir))

do for [clients in clients_values] {
    do for [overhead in overhead_values] {
        count = system(sprintf("awk -F, 'NR>1 && $3==%s && $4==%s' %s | wc -l", clients, overhead, csv)) + 0
        if (count == 0) { continue }

        outfile = sprintf("%s/result_packet_loss_qos_all_c%s_o%s.png", outdir, clients, overhead)
        set output outfile
        set multiplot layout 2,3 rowsfirst \
            title sprintf("Packet loss experiment: clients=%s, payload=%s bytes", clients, overhead) font ",16"

        do for [qos in qos_values] {
            cmd_mqtt = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT\"' %s", \
                clients, overhead, qos, csv)
            cmd_quic = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT-QUIC\"' %s", \
                clients, overhead, qos, csv)

            set title sprintf("Avg delay, QoS %s", qos)
            set xlabel "Intensity (req/s)"
            set ylabel "Delay (ms)"
            set xrange [100:1000]
            set yrange [*:*]
            set format y "%g"

            plot cmd_mqtt using 5:9 with linespoints lw 2 pt 7 ps 1.1 lc rgb "#d62728" title "MQTT", \
                 cmd_quic using 5:9 with linespoints lw 2 pt 5 ps 1.1 lc rgb "#1f77b4" title "MQTT-QUIC"
        }

        do for [qos in qos_values] {
            cmd_mqtt = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT\"' %s", \
                clients, overhead, qos, csv)
            cmd_quic = sprintf("< awk -F, 'NR>1 && $3==%s && $4==%s && $2==%s && $1==\"MQTT-QUIC\"' %s", \
                clients, overhead, qos, csv)

            set title sprintf("Loss ratio, QoS %s", qos)
            set xlabel "Intensity (req/s)"
            set ylabel "Loss ratio"
            set xrange [100:1000]
            set yrange [0:1]
            set format y "%.2f"

            plot cmd_mqtt using 5:8 with linespoints lw 2 pt 7 ps 1.1 lc rgb "#d62728" title "MQTT", \
                 cmd_quic using 5:8 with linespoints lw 2 pt 5 ps 1.1 lc rgb "#1f77b4" title "MQTT-QUIC"
        }

        unset multiplot
        unset output
        print sprintf("Saved %s", outfile)
    }
}
