set datafile separator ","
set terminal pngcairo size 1000,800 enhanced font 'Segoe UI,12'
set grid
set key top left box width 2 spacing 1.5

target_clients = 1

# CSV columns: Protocol,Clients,Intensity(req/sec),Sent,Received,LossRatio,AvgDelay(ms),AvgPacketSize(bytes)
cmd_mqtt = sprintf("< awk -F, 'NR>1 && $2==%d && $1==\"MQTT\"' experiment_results_packet_loss.csv", target_clients)
cmd_udp  = sprintf("< awk -F, 'NR>1 && $2==%d && $1==\"MQTT-UDP\"' experiment_results_packet_loss.csv", target_clients)

set xlabel "Интенсивность (запросов/с)"
set logscale x

# --- 1. Доля потерь ---
set output 'result_packet_loss_ratio.png'
set title sprintf("Клиенты: %d\nДоля потерь пакетов в реальной сети", target_clients) font ",14"
set ylabel "Доля потерь"
set yrange [0 : 1]
set format y "%.2f"

plot cmd_mqtt using 3:6 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red"   title "MQTT", \
     cmd_udp  using 3:6 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue"  title "MQTT-UDP"

# --- 2. Средняя задержка ---
set output 'result_packet_loss_delay.png'
set title sprintf("Клиенты: %d\nСредняя задержка в реальной сети", target_clients) font ",14"
set ylabel "Задержка (мс)"
set yrange [*:*]
set format y "%g"

plot cmd_mqtt using 3:7 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red"   title "MQTT", \
     cmd_udp  using 3:7 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue"  title "MQTT-UDP"

# --- 3. Средний размер пакета ---
set output 'result_packet_loss_size.png'
set title sprintf("Клиенты: %d\nСредний размер пакета в реальной сети", target_clients) font ",14"
set ylabel "Размер пакета (байт)"
set yrange [*:*]
set format y "%g"

plot cmd_mqtt using 3:8 with linespoints lw 2 pt 7 ps 1.5 lc rgb "red"   title "MQTT", \
     cmd_udp  using 3:8 with linespoints lw 2 pt 5 ps 1.5 lc rgb "blue"  title "MQTT-UDP"
