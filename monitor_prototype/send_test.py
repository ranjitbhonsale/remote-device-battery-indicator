import requests
import json
import argparse

def send_alert(topic, device, battery, server="https://ntfy.sh"):
    url = f"{server}/{topic}"
    
    payload = {
        "device": device,
        "battery": battery
    }
    
    headers = {
        "Title": "Battery Status Update",
        "Tags": "battery,warning" if battery <= 20 else "battery,ok"
    }
    
    print(f"Sending status for {device} (Battery: {battery}%) to {url}...")
    response = requests.post(
        url,
        data=json.dumps(payload),
        headers=headers
    )
    
    if response.status_code == 200:
        print("Success! Notification sent.")
    else:
        print(f"Failed to send notification. Status code: {response.status_code}")
        print(response.text)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Send test battery notifications to ntfy.sh")
    parser.add_argument("--topic", type=str, default="battery_alerts_hq_test_12345", help="ntfy.sh topic to publish to")
    parser.add_argument("--server", type=str, default="https://ntfy.sh", help="ntfy.sh server URL")
    parser.add_argument("--device", type=str, default="Test Device", help="Name of the device")
    parser.add_argument("--battery", type=int, default=15, help="Battery level to report")
    
    args = parser.parse_args()
    
    send_alert(args.topic, args.device, args.battery, args.server)
