# The talk lists stay fast as the data grows (the harness adds ~40 talks per tick). With 2 queries per talk,
# GET /talks took 12-16 s live and starved the 5-connection pool. Now 2 queries per list.

GET {{base_url}}/talks
HTTP 200
[Asserts]
duration < 3000

GET {{base_url}}/talks?sort=rating
HTTP 200
[Asserts]
duration < 3000
