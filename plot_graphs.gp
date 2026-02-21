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

    set title "Зависимость средней задержки от интенсивности"
    set xlabel "Интенсивность (запросов/с)"
    set ylabel "Задержка (мс)"

    plot 'experiment_results.csv' using 4:($2==loss && $3==target_clients && stringcolumn(1) eq "MQTT" ? $5 : 1/0) \
            with linespoints lw 2 pt 7 ps 1.5 lc rgb "red" title "MQTT", \
         '' using 4:($2==loss && $3==target_clients && stringcolumn(1) eq "MQTT-UDP" ? $5 : 1/0) \
            with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue" title "MQTT-UDP"

    set title "Зависимость среднего размера пакета от интенсивности"
    set ylabel "Размер пакета (байт)"

    plot 'experiment_results.csv' using 4:($2==loss && $3==target_clients && stringcolumn(1) eq "MQTT" ? $6 : 1/0) \
            with linespoints lw 2 pt 7 ps 1.5 lc rgb "red" title "MQTT", \
         '' using 4:($2==loss && $3==target_clients && stringcolumn(1) eq "MQTT-UDP" ? $6 : 1/0) \
            with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue" title "MQTT-UDP"

    unset multiplot
}