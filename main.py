# MQTT 设备数据模拟上报脚本（多设备并发版）
# 支持 40 台设备同时上报，每台设备独立线程
import paho.mqtt.client as mqtt
import time
import json
import random
import threading
import datetime

# ── MQTT 配置 ──────────────────────────────────────────────
MQTT_BROKER = "localhost"
MQTT_PORT   = 1883
REPORT_INTERVAL = 5   # 每台设备上报间隔（秒）
FALL_PROBABILITY = 0.02  # 随机跌倒概率（2%）

# ── 全部 40 台设备 ID ──────────────────────────────────────
DEVICE_IDS = (
    # 万柏林区（DEV-CF-001 ~ 010）
    [f"DEV-CF-{i:03d}" for i in range(1, 11)] +
    # 迎泽区（DEV-YZ-001 ~ 010）
    [f"DEV-YZ-{i:03d}" for i in range(1, 11)] +
    # 杏花岭区（DEV-XH-001 ~ 010）
    [f"DEV-XH-{i:03d}" for i in range(1, 11)] +
    # 晋源区（DEV-JY-001 ~ 010）
    [f"DEV-JY-{i:03d}" for i in range(1, 11)]
)

# ── 停止信号 ──────────────────────────────────────────────
stop_event = threading.Event()


def build_payload(device_id: str) -> str:
    is_fall = random.random() < FALL_PROBABILITY
    return json.dumps({
        "device_id": device_id,
        "timestamp": int(time.time() * 1000),
        "health_data": {
            "heart_rate_per_min":  random.randint(60, 100),
            "breath_rate_per_min": random.randint(12, 20),
            "is_fall":             is_fall,
            "is_person_present":   True,
        },
        "version": "1.0",
    })


def device_thread(device_id: str, start_delay: float):
    """每台设备独立运行的线程。"""
    # 错开启动时间，避免同一时刻大量设备同时连接
    time.sleep(start_delay)

    client = mqtt.Client(client_id=device_id)
    topic  = f"/device/{device_id}/data"

    def on_connect(c, userdata, flags, rc):
        if rc == 0:
            print(f"[{device_id}] 已连接")
        else:
            print(f"[{device_id}] 连接失败，rc={rc}")

    def on_disconnect(c, userdata, rc):
        if not stop_event.is_set():
            print(f"[{device_id}] 断开（rc={rc}），5 秒后重连...")

    client.on_connect    = on_connect
    client.on_disconnect = on_disconnect

    try:
        client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        client.loop_start()

        while not stop_event.is_set():
            payload = build_payload(device_id)
            result  = client.publish(topic, payload, qos=0)
            ts      = datetime.datetime.now().strftime("%H:%M:%S")
            if result.rc == 0:
                data = json.loads(payload)["health_data"]
                fall_tag = " ⚠️ FALL" if data["is_fall"] else ""
                print(f"[{ts}][{device_id}] HR={data['heart_rate_per_min']} "
                      f"BR={data['breath_rate_per_min']}{fall_tag}")
            else:
                print(f"[{ts}][{device_id}] 发布失败 rc={result.rc}")
            stop_event.wait(REPORT_INTERVAL)

    except Exception as e:
        print(f"[{device_id}] 异常: {e}")
    finally:
        client.loop_stop()
        client.disconnect()


def main():
    print(f"启动 {len(DEVICE_IDS)} 台设备模拟，上报间隔 {REPORT_INTERVAL}s，Ctrl+C 停止\n")

    threads = []
    for idx, device_id in enumerate(DEVICE_IDS):
        # 错开启动时间，避免同一时刻大量设备同时连接
        delay = idx * 0.2
        t = threading.Thread(target=device_thread, args=(device_id, delay),
                             daemon=True, name=device_id)
        t.start()
        threads.append(t)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n正在停止所有设备...")
        stop_event.set()
        for t in threads:
            t.join(timeout=3)
        print("所有设备已停止")


if __name__ == "__main__":
    main()
