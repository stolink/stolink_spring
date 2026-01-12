
import pika
import sys

def purge_queue(host, port, username, password, vhost, queue_name):
    try:
        credentials = pika.PlainCredentials(username, password)
        parameters = pika.ConnectionParameters(
            host=host,
            port=port,
            virtual_host=vhost,
            credentials=credentials
        )
        connection = pika.BlockingConnection(parameters)
        channel = connection.channel()

        # Check if queue exists passively (raises exception if not) or just try to purge
        try:
            channel.queue_purge(queue_name)
            print(f"Purged {queue_name} on {host}:{port}")
        except pika.exceptions.ChannelClosedByBroker as e:
            if e.reply_code == 404:
                print(f"Queue {queue_name} does not exist on {host}:{port}")
            else:
                print(f"Failed to purge {queue_name} on {host}:{port}: {e}")
        except Exception as e:
            print(f"Error purging {queue_name} on {host}:{port}: {e}")

        connection.close()
    except Exception as e:
        print(f"Connection failed to {host}:{port} for {queue_name}: {e}")

queues_config = [
    # Agent RabbitMQ (Port 5672)
    {
        "host": "localhost",
        "port": 5672,
        "user": "stolink",
        "pass": "stolink123",
        "vhost": "/",
        "queues": [
            "stolink.analysis.queue",
            "document_analysis_queue",
            "global_merge_queue",
            "analysis.completed",
            "analysis.dlq"
        ]
    },
    # Image RabbitMQ (Port 5673)
    {
        "host": "localhost",
        "port": 5673,
        "user": "stolink",
        "pass": "stolink123",
        "vhost": "/",
        "queues": [
            "stolink.image.queue"
        ]
    }
]

def main():
    print("Starting queue purge...")
    for config in queues_config:
        for queue in config["queues"]:
            purge_queue(
                config["host"],
                config["port"],
                config["user"],
                config["pass"],
                config["vhost"],
                queue
            )
    print("Queue purge completed.")

if __name__ == "__main__":
    main()
