set datafile separator ","
set terminal pngcairo size 1000,1200 enhanced font 'Segoe UI,12'
set grid
set key top left box width 2 spacing 1.5

overhead_min = 0
overhead_max = 65000

intensity_values = "1 5 10 20 30 40 50"
target_clients = 1

do for [intensity in intensity_values] {

    set output sprintf('result_packet_size_intensity_%s.png', intensity)
    set multiplot layout 2,1 title sprintf("Интенсивность: %s запр/с, Клиенты: %d", intensity, target_clients) font ",16"

    cmd_mqtt = sprintf("< awk -F, 'NR>1 && $4+0==%s && $3==%d && $1==\"MQTT\"' experiment_results_packet_size.csv", intensity, target_clients)
    cmd_udp  = sprintf("< awk -F, 'NR>1 && $4+0==%s && $3==%d && $1==\"MQTT-UDP\"' experiment_results_packet_size.csv", intensity, target_clients)

    set title "Зависимость средней задержки от размера полезной нагрузки"
    set xlabel "Размер полезной нагрузки (байт)"
    set ylabel "Задержка (мс)"
    set xrange [overhead_min : overhead_max]
    set yrange [0 : 100]

    plot cmd_mqtt using 2:5 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red"  title "MQTT", \
         cmd_udp  using 2:5 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue" title "MQTT-UDP"

    set title "Зависимость размера пакета от размера полезной нагрузки"
    set xlabel "Размер полезной нагрузки (байт)"
    set ylabel "Размер пакета (байт)"
    set xrange [overhead_min : overhead_max]
    set yrange [0 : overhead_max + 2000]

    plot cmd_mqtt using 2:6 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red"  title "MQTT", \
         cmd_udp  using 2:6 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue" title "MQTT-UDP"

    unset multiplot
}