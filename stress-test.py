import requests
import random
import string
from multiprocessing.dummy import Pool as ThreadPool
import datetime

servers = ["808" + str(i) for i in range(1,10)]
cities = [chr(i) for i in range(ord('A'),ord('P')+1)]


def get_random_string(length):
    letters = string.ascii_lowercase
    return ''.join(random.choice(letters) for i in range(length))


def get_random_phone_number():
    return ''.join(str(random.choice(range(10))) for i in range(10))


def random_ride_request():
    url = "http://localhost:" + random.choice(servers) + "/drives"

    firstName = get_random_string(8)
    lastName = get_random_string(8)
    phoneNumber = get_random_phone_number()
    [startingPoint, endingPoint] = random.sample(cities, 2)
    departureDate ="2020-" + str(random.choice(range(10,31))) + "-01"
    vacancies = str(random.choice(range(1,11)))
    permittedDeviation = str(random.choice(range(11)))

    payload="{\r\n    \
    \"driver\": {\r\n    \
    \"firstName\": \" " + firstName + "\",\r\n    \
    \"lastName\": \"" + lastName + "\",\r\n    \
    \"phoneNumber\": \"" + phoneNumber + "\" \r\n    },\r\n    \
    \"startingPoint\": \"" + startingPoint + "\",\r\n    \
    \"endingPoint\": \"" + endingPoint + "\",\r\n    \
    \"departureDate\": \"" + departureDate + "\",\r\n    \
    \"vacancies\": \"" + vacancies + "\",\r\n    \
    \"permittedDeviation\": \"" + permittedDeviation + "\"\r\n}"

    headers = {'Content-Type': 'application/json'}
    return "POST", url, headers, payload


def random_path_request():
    url = "http://localhost:" + random.choice(servers) + "/paths"

    firstName = get_random_string(8)
    lastName = get_random_string(8)
    phoneNumber = get_random_phone_number()
    departureDate ="2020-" + str(random.choice(range(10,31))) + "-01"
    num_cities = random.choice(range(2,6 + 1))
    path = ', '.join(map(lambda c: "\"" + c + "\"" ,random.sample(cities, num_cities)))

    payload="{\r\n    \
    \"passenger\": {\r\n    \
    \"firstName\": \"" + firstName + "\",\r\n    \
    \"lastName\": \""+ lastName +"\",\r\n    \
    \"phoneNumber\": \"" + phoneNumber + "\" \r\n    },\r\n    \
    \"departureDate\": \"" + departureDate + "\",\r\n    \
    \"cities\": [" + path + "]\r\n}"
    headers = {'Content-Type': 'application/json'}
    return "POST", url, headers, payload


def snapshot_request():
    url = "http://localhost:" + random.choice(servers) + "/snapshot"
    return "GET", url, {}, {}


def rest_request(req):
    msg, url, headers, payload = req
    return requests.request(msg, url, headers=headers, data=payload)


def distance(p1, p2, p0):
    (x1,y1) = p1
    (x2,y2) = p2
    (x0,y0) = p0
    numerator = abs(((x2-x1)*(y1-y0)) - ((x1-x0)*(y2-y1)))
    denominator = (((x2-x1) ** 2) + ((y2-y1) ** 2)) ** 0.5
    return numerator / denominator


if __name__ == "__main__":
    p = 1/3
    epochs = 50
    req_per_epoch = 100
    rest_requests = []

    for i in range(epochs):
        for j in range(req_per_epoch):
            [choice] = random.choices([0,1], weights = [p, 1 - p])
            req = random_ride_request() if choice == 1 else random_path_request()
            rest_requests.append(req)

        rest_requests.append(snapshot_request())

    pool = ThreadPool(4)
    results = pool.map(rest_request, rest_requests)
    pool.close()
    pool.join()

    stats = {"snapshot" : { "count" : 0, "time" : datetime.timedelta(), "successful": 0 },
               "drives" : { "count" : 0, "time" : datetime.timedelta(), "successful": 0 },
               "paths" : { "count" : 0, "time" : datetime.timedelta(), "successful": 0, "satisfied": 0 }}

    for res in results:
        type = res.url.split("/")[3]
        stats[type]["count"] = stats[type]["count"] + 1
        stats[type]["time"] = stats[type]["time"] + res.elapsed
        if res.ok:
            stats[type]["successful"] = stats[type]["successful"] + 1
            if type == "paths" and res.json()["satisfied"]:
                stats[type]["satisfied"] = stats[type]["satisfied"] + 1

    print("drives - num request: {}, successful requests: {}, average serving time: {}"
          .format(stats["drives"]["count"], stats["drives"]["successful"], stats["drives"]["time"]/stats["drives"]["count"]))
    print("paths - num request: {}, successful requests: {}, , average serving time: {}, satisfied paths: {}"
          .format(stats["paths"]["count"], stats["paths"]["successful"], stats["paths"]["time"]/stats["paths"]["count"], stats["paths"]["satisfied"]))
    print("snapshots - num request: {}, successful requests: {}, average serving time: {}"
          .format(stats["snapshot"]["count"], stats["snapshot"]["successful"], stats["snapshot"]["time"]/stats["snapshot"]["count"]))
