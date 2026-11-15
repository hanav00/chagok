#!/usr/bin/env bash
# 시연 녹화 때 Virtual Console 을 명령으로 움직인다. 기어·누적거리·배터리·동승석만 다룬다.
# Virtual Console 은 브라우저 신호가 끊기면 쓰기를 거절해서, 명령이 도는 동안 같은 신호를 1초마다 보낸다.
#
#   tools/demo-drive.sh setup        누적거리 23,419km 이상·기어 P·SOC 23%·동승석 셋 Occupied (샘플 넣기 전에)
#   tools/demo-drive.sh ignition     시동 안내 "업무로 기록됩니다" 한 번 (부팅 때 나오는 말을 흉내, 디버그 빌드)
#   tools/demo-drive.sh trip 12      D → 12km(계기판 60km/h) → P. 모두 7초 남짓
#   tools/demo-drive.sh d            기어 D (Gleo 에 말할 동안 달리는 상태로 두기)
#   tools/demo-drive.sh km 4         누적거리만 4km 올리기
#   tools/demo-drive.sh p            기어 P
#   tools/demo-drive.sh soc 81       배터리 SOC 바꾸기
#   tools/demo-drive.sh state        지금 기어·누적거리·SOC
set -euo pipefail

readonly CONSOLE="http://127.0.0.1:8088"
readonly PORT=8088
readonly SESSION_CLIENT="demo-drive"
readonly SAMPLE_END_KM=23419 # 시연 샘플 끝(23,418.2km)보다 크게. Virtual Console 은 1km 단위다
readonly START_SOC=23
readonly KM_STEP_SECONDS=0.25 # 13km 가 3초 남짓. 영상에서 기다리는 시간을 줄인다
readonly SETTLE_SECONDS=1     # 기어를 바꾼 뒤 앱이 출발·도착을 처리할 틈
readonly DRIVE_SPEED_KPH=60   # 달리는 동안 계기판에 보일 속도. 놔두면 줄어들어서 1km 마다 다시 넣는다
readonly ODOMETER_SETTLE_SECONDS=2 # 누적거리는 앱에 1초에 한 번만 간다. 마지막 값이 닿기 전에 주차하면 거리가 덜 잡힌다
readonly PASSENGER_SEATS=(passenger rear_left rear_right)

keep_session() {
  while true; do
    curl -s -o /dev/null -X POST "$CONSOLE/browser/session?client=$SESSION_CLIENT" || true
    sleep 1
  done
}

start_session() {
  adb forward "tcp:$PORT" "tcp:$PORT" >/dev/null
  keep_session &
  SESSION_PID=$!
  trap 'kill "$SESSION_PID" 2>/dev/null || true' EXIT
  sleep 1.5 # 첫 신호가 닿기 전에 쓰면 거절된다
}

put() {
  curl -sf -o /dev/null -X PUT "$CONSOLE$1" || {
    echo "실패: PUT $1 (에뮬레이터가 오래 켜져 있었다면 콜드 부팅)" >&2
    exit 1
  }
}

state_value() {
  curl -s "$CONSOLE/vehicle/state" | python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"
}

odometer() { state_value "int(d['odometer']['traveled_distance_km'])"; }

gear() {
  put "/vehicle/gear?gear=$1"
  echo "기어 $1"
  sleep "$SETTLE_SECONDS"
}

add_km() {
  local km=$1 now
  now=$(odometer)
  for ((i = 1; i <= km; i++)); do
    put "/vehicle/controls?speed_kph=$DRIVE_SPEED_KPH"
    put "/vehicle/odometer?traveled_distance_km=$((now + i))"
    sleep "$KM_STEP_SECONDS"
  done
  put "/vehicle/controls?speed_kph=0"
  sleep "$ODOMETER_SETTLE_SECONDS"
  echo "누적거리 $now → $((now + km))km"
}

set_soc() {
  put "/vehicle/ev-battery?field=soc&value=$1"
  echo "SOC $1%"
}

setup() {
  local now
  now=$(odometer)
  if ((now < SAMPLE_END_KM)); then
    put "/vehicle/odometer?traveled_distance_km=$SAMPLE_END_KM"
    now=$SAMPLE_END_KM
  fi
  put "/vehicle/gear?gear=Park"
  set_soc "$START_SOC"
  # 에뮬레이터는 착석 값이 거꾸로라 Occupied 로 둬야 앱이 "비었음"으로 읽고 지난 메모를 말한다
  for seat in "${PASSENGER_SEATS[@]}"; do
    put "/vehicle/seat?kind=occupied&area=$seat&value=true"
  done
  echo "준비 끝: 누적거리 ${now}km · 기어 P · 동승석 Occupied"
}

main() {
  local command=${1:-state}
  start_session
  case $command in
    setup) setup ;;
    ignition) adb shell am broadcast --user 10 -a kr.chagok.demo.IGNITION -n kr.chagok/.demo.DemoIgnitionReceiver >/dev/null && echo "시동 안내" ;;
    trip) gear Drive && add_km "${2:?km 를 적어 주세요}" && gear Park ;;
    d) gear Drive ;;
    p) gear Park ;;
    km) add_km "${2:?km 를 적어 주세요}" ;;
    soc) set_soc "${2:?SOC 를 적어 주세요}" ;;
    state) state_value "f\"기어 {(d['gear']['actual'] or {}).get('value')} · 누적거리 {d['odometer']['traveled_distance_km']}km · SOC {d['ev_battery']['state_of_charge']}%\"" ;;
    *) sed -n '2,12p' "$0" && exit 1 ;;
  esac
}

main "$@"
