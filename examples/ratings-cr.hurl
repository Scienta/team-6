# Ratings change request: our own examples (workflow/specs/ratings-cr.md, DESIGN.md D1204, D1206, D1208-D1210).
# Run: hurl --test --variable base_url=http://localhost:8080 examples/ratings-cr.hurl

POST {{base_url}}/speakers
{ "name": "Rated Speaker", "email": "ratings-cr-{{newUuid}}@example.com" }
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Tie breaker", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

# PO-2: one rating of 1 is written with two decimals
PUT {{base_url}}/talks/{{talk_id}}/ratings/1
{ "score": 1 }
HTTP 200

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
body contains "\"averageRating\":1.00"

PUT {{base_url}}/talks/{{talk_id}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/3
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/4
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/5
{ "score": 3 }
HTTP 200

# R1: 5 ratings (1,5,3,3,3): the 1 and the 5 are left out, 9/3
GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.ratingCount" == 5
body contains "\"averageRating\":3.00"

PUT {{base_url}}/talks/{{talk_id}}/ratings/6
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/7
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/8
{ "score": 3 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/9
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/10
{ "score": 4 }
HTTP 200

# R1, R2, R3: 10 ratings (1,5,3,3,3,3,3,3,4,4): 8 count, 26/8 = 3.25
GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.ratingCount" == 10
body contains "\"averageRating\":3.25"

# R2: change reviewer 10 from 4 to 3: 25/8 = 3.125, a tie, goes to the even neighbour 3.12
PUT {{base_url}}/talks/{{talk_id}}/ratings/10
{ "score": 3 }
HTTP 200

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
body contains "\"averageRating\":3.12"

# R2: reviewer 10 to 5: only one of the two 5s is left out, 27/8 = 3.375 -> 3.38
PUT {{base_url}}/talks/{{talk_id}}/ratings/10
{ "score": 5 }
HTTP 200

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
body contains "\"averageRating\":3.38"
