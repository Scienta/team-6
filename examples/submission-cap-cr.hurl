# Submission cap change request: our own examples (workflow/specs/submission-cap-cr.md, DESIGN.md D1105, D1106, D1306,
# D1354). R = rule, PO = product owner answer.
# Run: hurl --test --variable base_url=http://localhost:8080 examples/submission-cap-cr.hurl

# Setup: Ada (the speaker under test) and two other speakers with a talk each
POST {{base_url}}/speakers
{ "name": "Ada", "email": "cap-ada-{{newUuid}}@example.com" }
HTTP 201
[Captures]
ada: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Bob", "email": "cap-bob-{{newUuid}}@example.com" }
HTTP 201
[Captures]
bob: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Bob's talk", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{bob}} }
HTTP 201
[Captures]
bob_talk: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Cyd", "email": "cap-cyd-{{newUuid}}@example.com" }
HTTP 201
[Captures]
cyd: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Cyd's talk", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{cyd}} }
HTTP 201
[Captures]
cyd_talk: jsonpath "$.id"

# R1: Ada co-speaks on Bob's talk and submits 2 of her own: 3 counting talks
PUT {{base_url}}/talks/{{bob_talk}}/co-speakers/{{ada}}
HTTP 200

POST {{base_url}}/talks
{ "title": "Ada 1", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ada}} }
HTTP 201
[Captures]
ada_1: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Ada 2", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ada}} }
HTTP 201

# R1, R3: at the limit, no new talk of her own, and (PO-1) no co-speaking on a talk in the running
POST {{base_url}}/talks
{ "title": "Ada 3", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ada}} }
HTTP 409

PUT {{base_url}}/talks/{{cyd_talk}}/co-speakers/{{ada}}
HTTP 409

GET {{base_url}}/talks/{{cyd_talk}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 0

# PO-2: re-adding her to Bob's talk changes nothing
PUT {{base_url}}/talks/{{bob_talk}}/co-speakers/{{ada}}
HTTP 200

# PO-3: the main-speaker check wins over the limit
PUT {{base_url}}/talks/{{ada_1}}/co-speakers/{{ada}}
HTTP 400

# R6: her own talk list shows only her 2 talks
GET {{base_url}}/speakers/{{ada}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 2

# R2: taking her off Bob's talk frees a place, usable at once
DELETE {{base_url}}/talks/{{bob_talk}}/co-speakers/{{ada}}
HTTP 200

PUT {{base_url}}/talks/{{cyd_talk}}/co-speakers/{{ada}}
HTTP 200

# R2: Cyd withdraws, which frees Ada's place too
POST {{base_url}}/talks/{{cyd_talk}}/withdraw
{ "speakerId": {{cyd}} }
HTTP 200

POST {{base_url}}/talks
{ "title": "Ada 3 again", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{ada}} }
HTTP 201

# R3: back at the limit, but a rejected talk is fine
PUT {{base_url}}/talks/{{bob_talk}}/status
{ "status": "REJECTED" }
HTTP 200

PUT {{base_url}}/talks/{{bob_talk}}/co-speakers/{{ada}}
HTTP 200
