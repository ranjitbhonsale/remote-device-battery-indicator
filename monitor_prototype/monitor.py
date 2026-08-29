import requests
import json
import argparse
import sys
import datetime

def monitor_battery(topic, server="https://ntfy.sh", threshold=20):
    url = f"{server}/{topic}/json"
    print(f"[{datetime.datetime.now().strftime('%H:%M:%S')}] Starting low battery monitor...")
    print(f"Listening to: {url}")
    print(f"Low battery threshold: {threshold}%\n")
    print("Waiting for notifications... (Press Ctrl+C to stop)")
    
    try:
        # Long polling / streaming request
        response = requests.get(url, stream=True)
        for line in response.iter_lines():
            if line:
                data = json.loads(line)
                if data.get('event') == 'message':
                    message = data.get('message', '')
                    title = data.get('title', 'Notification')
                    timestamp = datetime.datetime.fromtimestamp(data.get('date', datetime.datetime.now().timestamp())).strftime('%H:%M:%S')
                    
                    # Attempt to parse the message as JSON for structured data
                    try:
                        payload = json.loads(message)
                        device_name = payload.get('device', title)
                        battery_level = payload.get('battery', None)
                        
                        if battery_level is not None:
                            if isinstance(battery_level, (int, float)):
                                if battery_level <= threshold:
                                    print(f"[{timestamp}] ⚠️ ALERT: {device_name} battery is LOW ({battery_level}%)")
                                else:
                                    print(f"[{timestamp}] ✅ {device_name} battery is OK ({battery_level}%)")
                        else:
                            print(f"[{timestamp}] {title}: {message}")
                            
                    except json.JSONDecodeError:
                        # Fallback for plain text messages
                        print(f"[{timestamp}] {title}: {message}")
                        if "low" in message.lower() or "battery" in message.lower():
                            print(f"    -> Possible battery alert detected!")
                            
    except KeyboardInterrupt:
        print("\nStopping monitor...")
        sys.exit(0)
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Monitor battery levels via ntfy.sh")
    parser.add_argument("--topic", type=str, default="battery_alerts_hq_test_12345", help="ntfy.sh topic to subscribe to")
    parser.add_argument("--server", type=str, default="https://ntfy.sh", help="ntfy.sh server URL (e.g., if self-hosted)")
    parser.add_argument("--threshold", type=int, default=20, help="Low battery threshold percentage (default: 20)")
    
    args = parser.parse_args()
    
    monitor_battery(args.topic, args.server, args.threshold)
