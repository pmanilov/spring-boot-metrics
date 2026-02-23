set datafile separator ","
set terminal pngcairo size 1000,1200 enhanced font 'Segoe UI,12'
set grid
set key top left box width 2 spacing 1.5
set key autotitle columnheader

loss_values = "0 10 20 30 50 70 80"
target_clients = 1

do for [loss in loss_values] {
    set output sprintf('result_loss_%s_percent.png', loss)
    set multiplot layout 2,1 title sprintf("Потери пакетов: %s%%, Клиенты: %d", loss, target_clients) font ",16"

    cmd_mqtt = sprintf("< awk -F, '$2==%s && $3==%d && $1==\"MQTT\"' experiment_results.csv", loss, target_clients)

    cmd_udp  = sprintf("< awk -F, '$2==%s && $3==%d && $1==\"MQTT-UDP\"' experiment_results.csv", loss, target_clients)

    set title "Зависимость средней задержки от интенсивности"
    set xlabel "Интенсивность (запросов/с)"
    set ylabel "Задержка (мс)"

    plot cmd_mqtt using 4:5 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red" title "MQTT", \
         cmd_udp  using 4:5 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue" title "MQTT-UDP"

    set title "Зависимость среднего размера пакета от интенсивности"
    set ylabel "Размер пакета (байт)"

    plot cmd_mqtt using 4:6 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red" title "MQTT", \
         cmd_udp  using 4:6 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue" title "MQTT-UDP"

    unset multiplot
}