# Battery Monitor Prototype

This is a simple Python prototype for monitoring battery levels from multiple devices using the [ntfy.sh](https://ntfy.sh) messaging platform. 

It uses long-polling/streaming to listen for incoming JSON messages on a specific ntfy.sh topic.

## Prerequisites

Install the required Python packages:

```bash
pip install -r requirements.txt
```

## How to run the monitor

Run the monitor script to start listening for incoming battery alerts:

```bash
python monitor.py --topic my_secret_battery_topic_123
```

By default, it listens to the topic `battery_alerts_hq_test_12345` and alerts on battery levels `<= 20%`.

### Options:
- `--topic`: The ntfy.sh topic to subscribe to.
- `--server`: The ntfy.sh server URL (if you are self-hosting, e.g., `https://ntfy.my-domain.com`).
- `--threshold`: The low battery threshold percentage (default is 20).

## How to test sending a notification

You can use the provided `send_test.py` script to simulate devices sending their battery levels:

```bash
# Send a low battery alert
python send_test.py --topic my_secret_battery_topic_123 --device "Living Room Tablet" --battery 15

# Send a normal battery status
python send_test.py --topic my_secret_battery_topic_123 --device "Kitchen Phone" --battery 85
```

Devices can send raw JSON payloads to the ntfy.sh topic in this format:
```json
{
  "device": "My Phone",
  "battery": 45
}
```
The monitor will automatically parse this JSON and determine if an alert needs to be triggered based on your threshold.
