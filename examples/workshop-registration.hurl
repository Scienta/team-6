# Workshop registration: our own examples (workflow/specs/workshop-registration.md). R = rule, PO = product owner answer.
# Run: hurl --test --variable base_url=http://localhost:8080 examples/workshop-registration.hurl

# Setup: a speaker with an accepted workshop (two reviewers), a submitted workshop, and a presentation
POST {{base_url}}/speakers
{ "name": "Workshop Host", "email": "ws-host-{{newUuid}}@example.com" }
HTTP 201
[Captures]
host_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Hands-on", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{host_id}} }
HTTP 201
[Captures]
ws_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Not yet", "format": "WORKSHOP", "level": "BEGINNER", "speakerId": {{host_id}} }
HTTP 201
[Captures]
submitted_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "A presentation", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{host_id}} }
HTTP 201
[Captures]
presentation_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{ws_id}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{ws_id}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{ws_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

# R7: no registrations yet
GET {{base_url}}/workshops/{{ws_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 0

# R1, R8: register; the email as typed
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Ada Lovelace", "email": "Ada-{{newUuid}}@Example.COM" }
HTTP 201
[Captures]
ada_id: jsonpath "$.id"
ada_uuid: jsonpath "$.email" regex "^Ada-(.+)@Example\\.COM$"
[Asserts]
jsonpath "$.workshopId" == {{ws_id}}
jsonpath "$.name" == "Ada Lovelace"
jsonpath "$.email" matches /^Ada-.*@Example\.COM$/

# R5, PO-3: the same email in other letter case is the existing registration, 200
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Ada L.", "email": "ADA-{{ada_uuid}}@example.com" }
HTTP 200
[Asserts]
jsonpath "$.id" == {{ada_id}}
jsonpath "$.name" == "Ada Lovelace"

# R1: name and email are required; PO-4: the email must look like an email
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "email": "x@example.com" }
HTTP 400

POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Ada", "email": "  " }
HTTP 400

POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Ada", "email": "ada@example" }
HTTP 400

# 404: unknown talk; PO-1: not a workshop is a 400; PO-2: a workshop that isn't accepted is a 409
POST {{base_url}}/workshops/999999999/registrations
{ "name": "Ada", "email": "ada@example.com" }
HTTP 404

POST {{base_url}}/workshops/{{presentation_id}}/registrations
{ "name": "Ada", "email": "ada@example.com" }
HTTP 400

POST {{base_url}}/workshops/{{submitted_id}}/registrations
{ "name": "Ada", "email": "ada@example.com" }
HTTP 409

# R7: in the order they registered
POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Grace Hopper", "email": "grace-{{newUuid}}@example.com" }
HTTP 201

GET {{base_url}}/workshops/{{ws_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2
jsonpath "$[0].name" == "Ada Lovelace"
jsonpath "$[1].name" == "Grace Hopper"

# R3: withdrawn later: the list stays, new registrations are refused
POST {{base_url}}/talks/{{ws_id}}/withdraw
{ "speakerId": {{host_id}} }
HTTP 200

GET {{base_url}}/workshops/{{ws_id}}/registrations
HTTP 200
[Asserts]
jsonpath "$" count == 2

POST {{base_url}}/workshops/{{ws_id}}/registrations
{ "name": "Edsger", "email": "edsger-{{newUuid}}@example.com" }
HTTP 409
