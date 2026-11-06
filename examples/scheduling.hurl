# Programme scheduling: our own examples (workflow/specs/scheduling.md). R = rule, PO-?n = open product owner question.
# Run: hurl --test --variable base_url=http://localhost:8080 examples/scheduling.hurl
# The programme is shared and has only 15 places, so this file first empties the ones it uses (204 or 404).
# Against the deployed service, harness traffic can fill a place between cleanup and use: rerun before suspecting the code.

DELETE {{base_url}}/programme/R1/S1
HTTP *
[Asserts]
status toString matches "^(204|404)$"

DELETE {{base_url}}/programme/R2/S1
HTTP *
[Asserts]
status toString matches "^(204|404)$"

DELETE {{base_url}}/programme/R3/S1
HTTP *
[Asserts]
status toString matches "^(204|404)$"

DELETE {{base_url}}/programme/R1/S2
HTTP *
[Asserts]
status toString matches "^(204|404)$"

DELETE {{base_url}}/programme/R3/S4
HTTP *
[Asserts]
status toString matches "^(204|404)$"

# Setup: Ada gives a talk with Bob as co-speaker; Bob and Cyd each have a talk; Cyd has a workshop.
POST {{base_url}}/speakers
{ "name": "Ada", "email": "sched-ada-{{newUuid}}@example.com" }
HTTP 201
[Captures]
ada: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Bob", "email": "sched-bob-{{newUuid}}@example.com" }
HTTP 201
[Captures]
bob: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Cyd", "email": "sched-cyd-{{newUuid}}@example.com" }
HTTP 201
[Captures]
cyd: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Ada and Bob", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ada}} }
HTTP 201
[Captures]
ada_talk: jsonpath "$.id"

PUT {{base_url}}/talks/{{ada_talk}}/co-speakers/{{bob}}
HTTP 200

POST {{base_url}}/talks
{ "title": "Bob alone", "format": "LIGHTNING_10", "level": "BEGINNER", "speakerId": {{bob}} }
HTTP 201
[Captures]
bob_talk: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Cyd alone", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{cyd}} }
HTTP 201
[Captures]
cyd_talk: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Cyd's workshop", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{cyd}} }
HTTP 201
[Captures]
workshop: jsonpath "$.id"

# R4: only accepted talks (this one is still SUBMITTED)
PUT {{base_url}}/programme/R1/S1
{ "talkId": {{ada_talk}} }
HTTP 409

# accept all four (two reviewers each)
PUT {{base_url}}/talks/{{ada_talk}}/ratings/1
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{ada_talk}}/ratings/2
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{ada_talk}}/status
{ "status": "ACCEPTED" }
HTTP 200
PUT {{base_url}}/talks/{{bob_talk}}/ratings/1
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{bob_talk}}/ratings/2
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{bob_talk}}/status
{ "status": "ACCEPTED" }
HTTP 200
PUT {{base_url}}/talks/{{cyd_talk}}/ratings/1
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{cyd_talk}}/ratings/2
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{cyd_talk}}/status
{ "status": "ACCEPTED" }
HTTP 200
PUT {{base_url}}/talks/{{workshop}}/ratings/1
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{workshop}}/ratings/2
{ "score": 4 }
HTTP 200
PUT {{base_url}}/talks/{{workshop}}/status
{ "status": "ACCEPTED" }
HTTP 200

# R3, R1, R2: put a talk in, look it up; times exactly HH:mm
PUT {{base_url}}/programme/R1/S1
{ "talkId": {{ada_talk}} }
HTTP 200
[Asserts]
jsonpath "$.room" == "R1"
jsonpath "$.slot" == "S1"
jsonpath "$.start" == "09:00"
jsonpath "$.end" == "10:00"
jsonpath "$.talkId" == {{ada_talk}}

GET {{base_url}}/programme/R1/S1
HTTP 200
[Asserts]
jsonpath "$.start" == "09:00"
jsonpath "$.talkId" == {{ada_talk}}

# PO-?1: the same put again is a 200, unchanged (scheduling/check-1 failed on 409)
PUT {{base_url}}/programme/R1/S1
{ "talkId": {{ada_talk}} }
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{ada_talk}}

GET {{base_url}}/programme/R1/S1
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{ada_talk}}

# R6: one talk per room and slot
PUT {{base_url}}/programme/R1/S1
{ "talkId": {{cyd_talk}} }
HTTP 409

# R7: a talk is on the programme only once
PUT {{base_url}}/programme/R1/S2
{ "talkId": {{ada_talk}} }
HTTP 409

# R8: Bob co-speaks Ada's talk in S1, so his own talk can't go in S1 too
PUT {{base_url}}/programme/R2/S1
{ "talkId": {{bob_talk}} }
HTTP 409

GET {{base_url}}/programme/R2/S1
HTTP 404

# R8: someone else in the same slot is fine
PUT {{base_url}}/programme/R2/S1
{ "talkId": {{cyd_talk}} }
HTTP 200

# R5: a workshop needs the long slot S4
PUT {{base_url}}/programme/R3/S1
{ "talkId": {{workshop}} }
HTTP 409

PUT {{base_url}}/programme/R3/S4
{ "talkId": {{workshop}} }
HTTP 200
[Asserts]
jsonpath "$.start" == "13:30"
jsonpath "$.end" == "15:30"

# R3, PO-?3: take the talk out (204), then it can go somewhere else
DELETE {{base_url}}/programme/R1/S1
HTTP 204

# PO-?2, PO-?3: an empty place is a 404
GET {{base_url}}/programme/R1/S1
HTTP 404

DELETE {{base_url}}/programme/R1/S1
HTTP 404

PUT {{base_url}}/programme/R1/S2
{ "talkId": {{ada_talk}} }
HTTP 200
[Asserts]
jsonpath "$.start" == "10:15"
jsonpath "$.end" == "11:15"

# R9: withdrawing a scheduled talk leaves the programme as it is
POST {{base_url}}/talks/{{ada_talk}}/withdraw
{ "speakerId": {{ada}} }
HTTP 200

GET {{base_url}}/programme/R1/S2
HTTP 200
[Asserts]
jsonpath "$.talkId" == {{ada_talk}}

# PO-?4: rooms and slots outside the venue
PUT {{base_url}}/programme/R4/S1
{ "talkId": {{cyd_talk}} }
HTTP 404

GET {{base_url}}/programme/R1/S6
HTTP 404

# 400: talkId missing or not a positive whole number
PUT {{base_url}}/programme/R1/S1
{ }
HTTP 400

PUT {{base_url}}/programme/R1/S1
{ "talkId": "{{cyd_talk}}" }
HTTP 400

PUT {{base_url}}/programme/R1/S1
{ "talkId": 0 }
HTTP 400

# PO-?5: a talk that doesn't exist is a 400, as an unknown speakerId in POST /talks
PUT {{base_url}}/programme/R1/S1
{ "talkId": 999999999 }
HTTP 400
