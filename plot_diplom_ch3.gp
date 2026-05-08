# Per-chapter figures for diplom chapter 3 (рисунки 3.2–3.11).
# Each output is a single PNG, ready to be embedded under the corresponding
# figure caption in diplom_chapter3.md.
#
# Edit the BASE_* constants below to choose the operating point used on
# "fixed-parameters" figures. The values must exist in the experiment grid:
#   N        ∈ {1, 2, 5, 10, 15}
#   S        ∈ {0, 2048, 4096, 8192}
#   λ/client ∈ {100, 200, ..., 1000}

set datafile separator ","
set terminal pngcairo size 1200,800 enhanced font "Sans,12"
set grid
set border linewidth 1.2
set tics out
set key top left box width 1.5 spacing 1.2

csv = "experiment_results_packet_loss_qos_all.csv"
outdir = "result_diplom_ch3"

BASE_N = 10
BASE_S = 2048
BASE_LAMBDA_PER_CLIENT = 500

system(sprintf("mkdir -p %s", outdir))

color_mqtt   = "#d62728"
color_quic   = "#1f77b4"
color_qos0   = "#2ca02c"
color_qos1   = "#ff7f0e"
color_qos2   = "#9467bd"

# CSV columns:
#  1 Protocol  2 QoS  3 Clients  4 Overhead(bytes)  5 IntensityPerClient
#  6 TargetAggregateRate  7 ActualSendRate  8 ActualReceiveRate
#  9 Sent  10 Received  11 LossRatio
# 12 AvgDelay  13 MedianDelay  14 P95Delay  15 P99Delay  16 AvgPacketSize

# ------------------------------------------------------------------
# Рис. 3.2 — задержка (медиана, 0,95, 0,99) от Λ; QoS 0; N, S фикс.
# ------------------------------------------------------------------
filt_proto(proto, qos, n, s) = sprintf( \
    "< awk -F, 'NR>1 && $1==\"%s\" && $2==%d && $3==%d && $4==%d' %s", \
    proto, qos, n, s, csv)

set output sprintf("%s/fig_3_2_delay_vs_lambda_qos0.png", outdir)
set title sprintf("Задержка доставки: MQTT/TCP и MQTT/QUIC; QoS 0; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:13 with linespoints lw 2 pt 7  ps 1.0 lc rgb color_mqtt title "MQTT/TCP, медиана", \
     filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:14 with linespoints lw 2 pt 9  ps 1.0 lc rgb color_mqtt dt 2 title "MQTT/TCP, q_{0,95}", \
     filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:15 with linespoints lw 2 pt 11 ps 1.0 lc rgb color_mqtt dt 3 title "MQTT/TCP, q_{0,99}", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:13 with linespoints lw 2 pt 5  ps 1.0 lc rgb color_quic title "MQTT/QUIC, медиана", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:14 with linespoints lw 2 pt 4  ps 1.0 lc rgb color_quic dt 2 title "MQTT/QUIC, q_{0,95}", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:15 with linespoints lw 2 pt 6  ps 1.0 lc rgb color_quic dt 3 title "MQTT/QUIC, q_{0,99}"
unset output

# Рис. 3.2-а — средняя задержка от Λ; QoS 0; N, S фикс.
set output sprintf("%s/fig_3_2_avg_delay_vs_lambda_qos0.png", outdir)
set title sprintf("Средняя задержка доставки: MQTT/TCP и MQTT/QUIC; QoS 0; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Средняя задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:12 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt title "MQTT/TCP", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:12 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic title "MQTT/QUIC"
unset output

# ------------------------------------------------------------------
# Рис. 3.3 — задержка (медиана, 0,99) от N; QoS 0; λ_per_client, S фикс.
# ------------------------------------------------------------------
filt_n(proto, qos, lambda_per_client, s) = sprintf( \
    "< awk -F, 'NR>1 && $1==\"%s\" && $2==%d && $5==%g && $4==%d' %s | sort -t, -k3,3n", \
    proto, qos, lambda_per_client, s, csv)

set output sprintf("%s/fig_3_3_delay_vs_clients_qos0.png", outdir)
set title sprintf("Задержка доставки от числа издателей N; QoS 0; λ/клиент = %d сообщ./с; S = %d байт", \
    BASE_LAMBDA_PER_CLIENT, BASE_S)
set xlabel "Число издателей N"
set ylabel "Задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_n("MQTT", 0, BASE_LAMBDA_PER_CLIENT, BASE_S)      using 3:13 with linespoints lw 2 pt 7 ps 1.2 lc rgb color_mqtt title "MQTT/TCP, медиана", \
     filt_n("MQTT", 0, BASE_LAMBDA_PER_CLIENT, BASE_S)      using 3:15 with linespoints lw 2 pt 11 ps 1.2 lc rgb color_mqtt dt 3 title "MQTT/TCP, q_{0,99}", \
     filt_n("MQTT-QUIC", 0, BASE_LAMBDA_PER_CLIENT, BASE_S) using 3:13 with linespoints lw 2 pt 5 ps 1.2 lc rgb color_quic title "MQTT/QUIC, медиана", \
     filt_n("MQTT-QUIC", 0, BASE_LAMBDA_PER_CLIENT, BASE_S) using 3:15 with linespoints lw 2 pt 6 ps 1.2 lc rgb color_quic dt 3 title "MQTT/QUIC, q_{0,99}"
unset output

# Рис. 3.3-а — средняя задержка от N; QoS 0; λ_per_client, S фикс.
set output sprintf("%s/fig_3_3_avg_delay_vs_clients_qos0.png", outdir)
set title sprintf("Средняя задержка доставки от N; QoS 0; λ/клиент = %d сообщ./с; S = %d байт", \
    BASE_LAMBDA_PER_CLIENT, BASE_S)
set xlabel "Число издателей N"
set ylabel "Средняя задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_n("MQTT", 0, BASE_LAMBDA_PER_CLIENT, BASE_S)      using 3:12 with linespoints lw 2 pt 7 ps 1.2 lc rgb color_mqtt title "MQTT/TCP", \
     filt_n("MQTT-QUIC", 0, BASE_LAMBDA_PER_CLIENT, BASE_S) using 3:12 with linespoints lw 2 pt 5 ps 1.2 lc rgb color_quic title "MQTT/QUIC"
unset output

# Графики vs S убраны: основные зависимости в дипломе строятся от интенсивности на одного издателя λ.
# Влияние S отражено в составе сводных пограничных случаев (см. plot_packet_loss_qos_all.gp).

# ------------------------------------------------------------------
# Рис. 3.5 — медиана задержки QoS 0 vs QoS 1 от λ; N, S фикс.
# ------------------------------------------------------------------
set output sprintf("%s/fig_3_5_median_qos0_qos1.png", outdir)
set title sprintf("Медиана задержки доставки при QoS 0 и QoS 1; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Медиана задержки, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:13 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt title "MQTT/TCP, QoS 0", \
     filt_proto("MQTT", 1, BASE_N, BASE_S)      using 5:13 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt dt 2 title "MQTT/TCP, QoS 1", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:13 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic title "MQTT/QUIC, QoS 0", \
     filt_proto("MQTT-QUIC", 1, BASE_N, BASE_S) using 5:13 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic dt 2 title "MQTT/QUIC, QoS 1"
unset output

# Рис. 3.5-а — средняя задержка QoS 0 vs QoS 1 от Λ; N, S фикс.
set output sprintf("%s/fig_3_5_avg_qos0_qos1.png", outdir)
set title sprintf("Средняя задержка доставки при QoS 0 и QoS 1; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Средняя задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:12 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt title "MQTT/TCP, QoS 0", \
     filt_proto("MQTT", 1, BASE_N, BASE_S)      using 5:12 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt dt 2 title "MQTT/TCP, QoS 1", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:12 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic title "MQTT/QUIC, QoS 0", \
     filt_proto("MQTT-QUIC", 1, BASE_N, BASE_S) using 5:12 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic dt 2 title "MQTT/QUIC, QoS 1"
unset output

# ------------------------------------------------------------------
# Рис. 3.6 — q_{0,95} от N; QoS 1; λ_per_client, S фикс.
# ------------------------------------------------------------------
set output sprintf("%s/fig_3_6_p95_vs_clients_qos1.png", outdir)
set title sprintf("Квантиль 0,95 задержки от N; QoS 1; λ/клиент = %d сообщ./с; S = %d байт", \
    BASE_LAMBDA_PER_CLIENT, BASE_S)
set xlabel "Число издателей N"
set ylabel "Задержка q_{0,95}, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_n("MQTT", 1, BASE_LAMBDA_PER_CLIENT, BASE_S)      using 3:14 with linespoints lw 2 pt 7 ps 1.2 lc rgb color_mqtt title "MQTT/TCP", \
     filt_n("MQTT-QUIC", 1, BASE_LAMBDA_PER_CLIENT, BASE_S) using 3:14 with linespoints lw 2 pt 5 ps 1.2 lc rgb color_quic title "MQTT/QUIC"
unset output

# Рис. 3.6-а — средняя задержка от N; QoS 1; λ_per_client, S фикс.
set output sprintf("%s/fig_3_6_avg_vs_clients_qos1.png", outdir)
set title sprintf("Средняя задержка от N; QoS 1; λ/клиент = %d сообщ./с; S = %d байт", \
    BASE_LAMBDA_PER_CLIENT, BASE_S)
set xlabel "Число издателей N"
set ylabel "Средняя задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_n("MQTT", 1, BASE_LAMBDA_PER_CLIENT, BASE_S)      using 3:12 with linespoints lw 2 pt 7 ps 1.2 lc rgb color_mqtt title "MQTT/TCP", \
     filt_n("MQTT-QUIC", 1, BASE_LAMBDA_PER_CLIENT, BASE_S) using 3:12 with linespoints lw 2 pt 5 ps 1.2 lc rgb color_quic title "MQTT/QUIC"
unset output

# ------------------------------------------------------------------
# Рис. 3.8 — медиана QoS 0/1/2 от λ; N, S фикс.
# ------------------------------------------------------------------
set output sprintf("%s/fig_3_8_median_qos_compare.png", outdir)
set title sprintf("Медиана задержки при QoS 0, 1, 2; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Медиана задержки, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
set key top left maxrows 3
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:13 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos0 title "MQTT/TCP, QoS 0", \
     filt_proto("MQTT", 1, BASE_N, BASE_S)      using 5:13 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos1 title "MQTT/TCP, QoS 1", \
     filt_proto("MQTT", 2, BASE_N, BASE_S)      using 5:13 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos2 title "MQTT/TCP, QoS 2", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:13 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos0 dt 2 title "MQTT/QUIC, QoS 0", \
     filt_proto("MQTT-QUIC", 1, BASE_N, BASE_S) using 5:13 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos1 dt 2 title "MQTT/QUIC, QoS 1", \
     filt_proto("MQTT-QUIC", 2, BASE_N, BASE_S) using 5:13 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos2 dt 2 title "MQTT/QUIC, QoS 2"
set key top left maxrows auto
unset output

# Рис. 3.8-а — средняя задержка QoS 0/1/2 от Λ; N, S фикс.
set output sprintf("%s/fig_3_8_avg_qos_compare.png", outdir)
set title sprintf("Средняя задержка при QoS 0, 1, 2; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Средняя задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
set key top left maxrows 3
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:12 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos0 title "MQTT/TCP, QoS 0", \
     filt_proto("MQTT", 1, BASE_N, BASE_S)      using 5:12 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos1 title "MQTT/TCP, QoS 1", \
     filt_proto("MQTT", 2, BASE_N, BASE_S)      using 5:12 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos2 title "MQTT/TCP, QoS 2", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:12 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos0 dt 2 title "MQTT/QUIC, QoS 0", \
     filt_proto("MQTT-QUIC", 1, BASE_N, BASE_S) using 5:12 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos1 dt 2 title "MQTT/QUIC, QoS 1", \
     filt_proto("MQTT-QUIC", 2, BASE_N, BASE_S) using 5:12 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos2 dt 2 title "MQTT/QUIC, QoS 2"
set key top left maxrows auto
unset output

# ------------------------------------------------------------------
# Рис. 3.9 — q_{0,99} от Λ; QoS 2; N, S фикс.
# ------------------------------------------------------------------
set output sprintf("%s/fig_3_9_p99_vs_lambda_qos2.png", outdir)
set title sprintf("Квантиль 0,99 задержки при QoS 2; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Задержка q_{0,99}, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_proto("MQTT", 2, BASE_N, BASE_S)      using 5:15 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt title "MQTT/TCP", \
     filt_proto("MQTT-QUIC", 2, BASE_N, BASE_S) using 5:15 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic title "MQTT/QUIC"
unset output

# Рис. 3.9-а — средняя задержка от Λ; QoS 2; N, S фикс.
set output sprintf("%s/fig_3_9_avg_vs_lambda_qos2.png", outdir)
set title sprintf("Средняя задержка при QoS 2; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Средняя задержка, мс"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
plot filt_proto("MQTT", 2, BASE_N, BASE_S)      using 5:12 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt title "MQTT/TCP", \
     filt_proto("MQTT-QUIC", 2, BASE_N, BASE_S) using 5:12 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic title "MQTT/QUIC"
unset output

# ------------------------------------------------------------------
# Рис. 3.10 — Λ_out от Λ для QoS 0/1/2; N, S фикс. С линией y=x.
# ------------------------------------------------------------------
set output sprintf("%s/fig_3_10_output_vs_lambda.png", outdir)
set title sprintf("Выходная интенсивность от заданной Λ; QoS 0, 1, 2; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Λ_{out}, сообщ./с"
set format y "%g"
set xrange [0:*]; set yrange [0:*]
set key top left maxrows 3
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:8 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos0 title "MQTT/TCP, QoS 0", \
     filt_proto("MQTT", 1, BASE_N, BASE_S)      using 5:8 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos1 title "MQTT/TCP, QoS 1", \
     filt_proto("MQTT", 2, BASE_N, BASE_S)      using 5:8 with linespoints lw 2 pt 7 ps 1.0 lc rgb color_qos2 title "MQTT/TCP, QoS 2", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:8 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos0 dt 2 title "MQTT/QUIC, QoS 0", \
     filt_proto("MQTT-QUIC", 1, BASE_N, BASE_S) using 5:8 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos1 dt 2 title "MQTT/QUIC, QoS 1", \
     filt_proto("MQTT-QUIC", 2, BASE_N, BASE_S) using 5:8 with linespoints lw 2 pt 5 ps 1.0 lc rgb color_qos2 dt 2 title "MQTT/QUIC, QoS 2", \
     BASE_N*x with lines lw 1 dt 4 lc rgb "#888888" title "Λ_{out} = λ × N"
set key top left maxrows auto
unset output

# ------------------------------------------------------------------
# Рис. 3.11 — доля доставленных сообщений от Λ; QoS 0; N, S фикс.
# Доля доставки DR = 1 - LossRatio (col 11).
# ------------------------------------------------------------------
set output sprintf("%s/fig_3_11_delivery_vs_lambda_qos0.png", outdir)
set title sprintf("Доля доставленных сообщений DR; QoS 0; N = %d; S = %d байт", BASE_N, BASE_S)
set xlabel "Интенсивность на одного издателя λ, сообщ./с"
set ylabel "Доля доставленных сообщений DR"
set format y "%.2f"
set xrange [0:*]; set yrange [0:1.05]
plot filt_proto("MQTT", 0, BASE_N, BASE_S)      using 5:(1-$11) with linespoints lw 2 pt 7 ps 1.0 lc rgb color_mqtt title "MQTT/TCP", \
     filt_proto("MQTT-QUIC", 0, BASE_N, BASE_S) using 5:(1-$11) with linespoints lw 2 pt 5 ps 1.0 lc rgb color_quic title "MQTT/QUIC"
unset output

print sprintf("Saved per-chapter figures into %s/", outdir)
