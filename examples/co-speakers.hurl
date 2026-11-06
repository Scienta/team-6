# Co-speakers: our own examples (workflow/specs/co-speakers.md). R = rule from the feature request.
# Run: hurl --test --variable base_url=http://localhost:8080 examples/co-speakers.hurl

# Setup: four speakers
POST {{base_url}}/speakers
{ "name": "Main Speaker", "email": "main-{{newUuid}}@example.com" }
HTTP 201
[Captures]
main_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Co One", "email": "co1-{{newUuid}}@example.com" }
HTTP 201
[Captures]
co1_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Co Two", "email": "co2-{{newUuid}}@example.com" }
HTTP 201
[Captures]
co2_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Co Three", "email": "co3-{{newUuid}}@example.com" }
HTTP 201
[Captures]
co3_id: jsonpath "$.id"

# R4: a new talk has coSpeakerIds []
POST {{base_url}}/talks
{ "title": "Pair programming", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{main_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"
[Asserts]
jsonpath "$.coSpeakerIds" count == 0

# R1, R4: add two, in order
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{co2_id}}
HTTP 200
[Asserts]
jsonpath "$.id" == {{talk_id}}
jsonpath "$.speakerId" == {{main_id}}
jsonpath "$.coSpeakerIds" count == 1
jsonpath "$.coSpeakerIds[0]" == {{co2_id}}

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{co1_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
jsonpath "$.coSpeakerIds[0]" == {{co2_id}}
jsonpath "$.coSpeakerIds[1]" == {{co1_id}}

# R5: adding again changes nothing, even when full
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{co2_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
jsonpath "$.coSpeakerIds[0]" == {{co2_id}}
jsonpath "$.coSpeakerIds[1]" == {{co1_id}}

# R2: a third is refused
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{co3_id}}
HTTP 409

# R3, PO-2: the main speaker can't co-speak on their own talk: 400, and nothing changes,
# also when the talk already has 2 co-speakers (as here)
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{main_id}}
HTTP 400

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
jsonpath "$.coSpeakerIds" not contains {{main_id}}

# Unknown talk or speaker
PUT {{base_url}}/talks/999999999/co-speakers/{{co1_id}}
HTTP 404

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/999999999
HTTP 404

DELETE {{base_url}}/talks/999999999/co-speakers/{{co1_id}}
HTTP 404

# R4: shown on every view of the talk
GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
jsonpath "$.coSpeakerIds[0]" == {{co2_id}}

GET {{base_url}}/talks?speakerId={{main_id}}
HTTP 200
[Asserts]
jsonpath "$[0].coSpeakerIds" count == 2

GET {{base_url}}/speakers/{{main_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 1
jsonpath "$[0].coSpeakerIds" count == 2

# R6: a co-speaker's own talk list doesn't include talks they only co-speak on
GET {{base_url}}/speakers/{{co1_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 0

GET {{base_url}}/talks?speakerId={{co1_id}}
HTTP 200
[Asserts]
jsonpath "$" count == 0

# R1: remove one; the order of the rest is kept
DELETE {{base_url}}/talks/{{talk_id}}/co-speakers/{{co2_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 1
jsonpath "$.coSpeakerIds[0]" == {{co1_id}}

# PO-1: removing someone who isn't a co-speaker is a 404 and changes nothing
DELETE {{base_url}}/talks/{{talk_id}}/co-speakers/{{co2_id}}
HTTP 404

GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 1

# Now there's room again; the newly added goes last
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{co3_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{co1_id}}
jsonpath "$.coSpeakerIds[1]" == {{co3_id}}

# R7: co1 co-speaks, and can still submit 3 talks of their own; a 4th is refused
POST {{base_url}}/talks
{ "title": "Own 1", "format": "LIGHTNING_10", "level": "BEGINNER", "speakerId": {{co1_id}} }
HTTP 201

POST {{base_url}}/talks
{ "title": "Own 2", "format": "LIGHTNING_10", "level": "BEGINNER", "speakerId": {{co1_id}} }
HTTP 201

POST {{base_url}}/talks
{ "title": "Own 3", "format": "LIGHTNING_10", "level": "BEGINNER", "speakerId": {{co1_id}} }
HTTP 201

POST {{base_url}}/talks
{ "title": "Own 4", "format": "LIGHTNING_10", "level": "BEGINNER", "speakerId": {{co1_id}} }
HTTP 409

# R7: a speaker at the limit can still co-speak on another talk
POST {{base_url}}/talks
{ "title": "Another", "format": "WORKSHOP", "level": "ADVANCED", "speakerId": {{main_id}} }
HTTP 201
[Captures]
talk2_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{talk2_id}}/co-speakers/{{co1_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{co1_id}}
