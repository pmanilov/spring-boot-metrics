set datafile separator ","
set terminal pngcairo size 1000,1200 enhanced font 'Segoe UI,12'
set grid
set key top left box width 2 spacing 1.5

csv = "experiment_results_packet_loss_qos1.csv"

clients_values  = "1 10 50 100"
overhead_values = "0 1024 2048 4096 16384"

# CSV: Protocol(1),Clients(2),Overhead(3),Intensity(4),Sent(5),Received(6),LossRatio(7),AvgDelay(8),AvgPacketSize(9)

do for [clients in clients_values] {
    do for [overhead in overhead_values] {

        count = system(sprintf("awk -F, 'NR>1 && $2==%s && $3==%s' ".csv." | wc -l", clients, overhead)) + 0
        if (count == 0) { continue }

        set output sprintf('result_packet_loss_qos1_c%s_o%s.png', clients, overhead)
        set multiplot layout 2,1 \
            title sprintf("QoS 1  Клиенты: %s, Размер полезной нагрузки: %s байт", clients, overhead) font ",16"

        cmd_mqtt = sprintf("< awk -F, 'NR>1 && $2==%s && $3==%s && $1==\"MQTT\"'      ".csv, clients, overhead)
        cmd_udp  = sprintf("< awk -F, 'NR>1 && $2==%s && $3==%s && $1==\"MQTT-UDP\"'  ".csv, clients, overhead)

        set title "Зависимость средней задержки от интенсивности"
        set xlabel "Интенсивность (запросов/с)"
        set ylabel "Задержка (мс)"
        set xrange [1000:10000]
        set yrange [*:*]
        set format y "%g"

        plot cmd_mqtt using 4:8 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red"   title "MQTT", \
             cmd_udp  using 4:8 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue"  title "MQTT-UDP"

        set title "Зависимость доли потерь от интенсивности"
        set xlabel "Интенсивность (запросов/с)"
        set ylabel "Доля потерь"
        set yrange [0:1]
        set format y "%.2f"

        plot cmd_mqtt using 4:7 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red"   title "MQTT", \
             cmd_udp  using 4:7 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue"  title "MQTT-UDP"

        unset multiplot
    }
}