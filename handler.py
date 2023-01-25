def handle(event, context):
    """handle a request to the function
    Args:
        event (dict): request params
        context (dict): function call metadata
    """

    return {
        "body": "Hello From Python3 Quentin et Sophie",
        "headers": {
            "Content-Type": ["text/plain"],
        }
    }
