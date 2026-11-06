# Programme export: our own examples (workflow/specs/programme-export.md, DESIGN.md D2000-D2086). PO = product owner answer.
# Run: hurl --test --variable base_url=http://localhost:8080 examples/programme-export.hurl

# Setup: an accepted talk with a comma and quotes in its title, by a speaker with a non-ASCII name, placed in R3/S5
POST {{base_url}}/speakers
{ "name": "Åse Ødegård", "email": "export-{{newUuid}}@example.com" }
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Kotlin, \"really\" fast", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{speaker_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{talk_id}}/ratings/1
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/2
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

# Free the place first, so the file can run again (204 when it was taken, 404 when it was empty)
DELETE {{base_url}}/programme/R3/S5
HTTP *

PUT {{base_url}}/programme/R3/S5
{ "talkId": {{talk_id}} }
HTTP 200

# R1-R5: the JSON row, fields as the spec lists them
GET {{base_url}}/programme
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$[?(@.talkId == {{talk_id}})].slot" == "S5"
jsonpath "$[?(@.talkId == {{talk_id}})].start" == "15:45"
jsonpath "$[?(@.talkId == {{talk_id}})].end" == "16:45"
jsonpath "$[?(@.talkId == {{talk_id}})].room" == "R3"
jsonpath "$[?(@.talkId == {{talk_id}})].title" == "Kotlin, \"really\" fast"
jsonpath "$[?(@.talkId == {{talk_id}})].speakers[0]" == "Åse Ødegård"

# PO-3: the JSON view is 406 when the client won't take JSON
GET {{base_url}}/programme
Accept: text/csv
HTTP 406

# R6-R8, PO-2, PO-4: CSV, also when asked for JSON; exact header; RFC 4180 quoting; UTF-8
GET {{base_url}}/programme.csv
Accept: application/json
HTTP 200
[Asserts]
header "Content-Type" matches /^text\/csv; ?charset=UTF-8$/
header "Content-Disposition" == "attachment; filename=programme.csv"
body startsWith "slot,start,end,room,talkId,title,format,speakers\n"
body contains "S5,15:45,16:45,R3,{{talk_id}},\"Kotlin, \"\"really\"\" fast\",PRESENTATION,Åse Ødegård\n"
body endsWith "\n"
