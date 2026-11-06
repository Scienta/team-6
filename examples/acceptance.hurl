# Acceptance: our own examples (workflow/specs/acceptance.md). R = rule, PO = product owner answer.
# Run: hurl --test --variable base_url=http://localhost:8080 examples/acceptance.hurl

# Setup: a speaker, someone else, and three talks
POST {{base_url}}/speakers
{ "name": "Speaker", "email": "acc-speaker-{{newUuid}}@example.com" }
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Someone Else", "email": "acc-other-{{newUuid}}@example.com" }
HTTP 201
[Captures]
other_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Talk A", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
a_id: jsonpath "$.id"
[Asserts]
jsonpath "$.status" == "SUBMITTED"

POST {{base_url}}/talks
{ "title": "Talk B", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
b_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Talk C", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
c_id: jsonpath "$.id"

# 400: not a decision
PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "SUBMITTED" }
HTTP 400

PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "accepted" }
HTTP 400

# 404: unknown talk
PUT {{base_url}}/talks/999999999/status
{ "status": "REJECTED" }
HTTP 404

# R3: accepting needs two different reviewers; one reviewer twice is one
PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "ACCEPTED" }
HTTP 409

PUT {{base_url}}/talks/{{a_id}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{a_id}}/ratings/1
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "ACCEPTED" }
HTTP 409

PUT {{base_url}}/talks/{{a_id}}/ratings/2
{ "score": 3 }
HTTP 200

# R1, R2: waitlist, then accept from the waitlist
PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "WAITLISTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "WAITLISTED"

# PO-1: the same decision again is a 200, unchanged
PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "WAITLISTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "WAITLISTED"

PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "ACCEPTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"

# R2: accepted is final; R8: refused changes nothing
PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "REJECTED" }
HTTP 409

PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "ACCEPTED" }
HTTP 200
[Asserts]
jsonpath "$.status" == "ACCEPTED"

# R7: the status shows everywhere
GET {{base_url}}/speakers/{{speaker_id}}/talks
HTTP 200
[Asserts]
jsonpath "$[0].status" == "ACCEPTED"

GET {{base_url}}/talks?speakerId={{speaker_id}}
HTTP 200
[Asserts]
jsonpath "$[0].status" == "ACCEPTED"

# R5, PO-3: only the speaker can withdraw; an unregistered id is 403 too
POST {{base_url}}/talks/{{a_id}}/withdraw
{ "speakerId": {{other_id}} }
HTTP 403

POST {{base_url}}/talks/{{a_id}}/withdraw
{ "speakerId": 999999999 }
HTTP 403

POST {{base_url}}/talks/{{a_id}}/withdraw
{}
HTTP 400

# R4: the speaker withdraws an accepted talk
POST {{base_url}}/talks/{{a_id}}/withdraw
{ "speakerId": {{speaker_id}} }
HTTP 200
[Asserts]
jsonpath "$.status" == "WITHDRAWN"

# R6: withdrawn is out for good
POST {{base_url}}/talks/{{a_id}}/withdraw
{ "speakerId": {{speaker_id}} }
HTTP 409

PUT {{base_url}}/talks/{{a_id}}/status
{ "status": "WAITLISTED" }
HTTP 409

# R4: a rejected talk can't be withdrawn; PO-1 reject again is fine
PUT {{base_url}}/talks/{{b_id}}/status
{ "status": "REJECTED" }
HTTP 200

PUT {{base_url}}/talks/{{b_id}}/status
{ "status": "REJECTED" }
HTTP 200

POST {{base_url}}/talks/{{b_id}}/withdraw
{ "speakerId": {{speaker_id}} }
HTTP 409

GET {{base_url}}/talks/{{b_id}}
HTTP 200
[Asserts]
jsonpath "$.status" == "REJECTED"

# PO-2: withdrawn (A) and rejected (B) don't count; C counts, so two more fit, then the limit
POST {{base_url}}/talks
{ "title": "Talk D", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201

POST {{base_url}}/talks
{ "title": "Talk E", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201

POST {{base_url}}/talks
{ "title": "Talk F", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 409

GET {{base_url}}/speakers/{{speaker_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 5
