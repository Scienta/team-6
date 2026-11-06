# Spring Boot 4 upgrade: behaviour that must not change (workflow/specs/spring-boot-4.md, pins P1-P16).
# Passes on Boot 2.7 / Java 11 and must pass unchanged after the upgrade. The cases come from the base specs
# and from what the harness sends (our request log, ticks 21-40).
# Run: hurl --test --variable base_url=http://localhost:8080 examples/spring-boot-4.hurl

# Setup: a speaker
POST {{base_url}}/speakers
{
  "name": "Margaret Hamilton",
  "email": "margaret-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
speaker_id: jsonpath "$.id"
email: jsonpath "$.email"

# P4: a trailing slash works on the speaker URLs, POST included
POST {{base_url}}/speakers/
{
  "name": "Alan Kay",
  "email": "alan-{{newUuid}}@example.com"
}
HTTP 201
[Captures]
slash_id: jsonpath "$.id"

GET {{base_url}}/speakers/{{slash_id}}/
HTTP 200
[Asserts]
jsonpath "$.id" == {{slash_id}}
jsonpath "$.name" == "Alan Kay"
jsonpath "$.bio" == null

# P5: a real name and a real email are required, the bio is at most 500 characters
POST {{base_url}}/speakers
{
  "email": "p5-{{newUuid}}@example.com"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": null,
  "email": "p5-{{newUuid}}@example.com"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": "",
  "email": "p5-{{newUuid}}@example.com"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": " \t ",
  "email": "p5-{{newUuid}}@example.com"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": "Barbara Hamilton"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": "Barbara Hamilton",
  "email": null
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": "Barbara Hamilton",
  "email": "p5-{{newUuid}}@example.com",
  "bio": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
HTTP 400

POST {{base_url}}/speakers
{
  "name": "Barbara Hamilton",
  "email": "p5-{{newUuid}}@example.com",
  "bio": "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy"
}
HTTP 201

# P6: one email is one speaker, whatever the letter case
POST {{base_url}}/speakers
{
  "name": "Margaret Again",
  "email": "MARGARET-{{newUuid}}@EXAMPLE.COM"
}
HTTP 201
[Captures]
upper_email: jsonpath "$.email"

POST {{base_url}}/speakers
{
  "name": "Margaret Twice",
  "email": "{{upper_email}}"
}
HTTP 409

# P7: invalid talks are rejected
POST {{base_url}}/talks
{
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": null,
  "format": "WORKSHOP",
  "level": "INTERMEDIATE",
  "speakerId": {{speaker_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "No speaker",
  "format": "PRESENTATION",
  "level": "ADVANCED"
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "Lowercase level",
  "format": "LIGHTNING_10",
  "level": "intermediate",
  "speakerId": {{speaker_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "Keynotes are invited",
  "format": "KEYNOTE",
  "level": "INTERMEDIATE",
  "speakerId": {{speaker_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "Unknown level",
  "format": "WORKSHOP",
  "level": "EXPERT",
  "speakerId": {{speaker_id}}
}
HTTP 400

POST {{base_url}}/talks
{
  "title": "Unknown speaker",
  "format": "WORKSHOP",
  "level": "BEGINNER",
  "speakerId": 999999999
}
HTTP 400

# P8 (and P1, P2 on the way): three talks, then the fourth is refused
POST {{base_url}}/talks
{
  "title": "One",
  "format": "LIGHTNING_10",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
talk_1: jsonpath "$.id"
[Asserts]
jsonpath "$.submittedAt" matches /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}\+00:00$/
jsonpath "$.submittedOn" matches /^\d{4}-\d{2}-\d{2}$/
jsonpath "$.durationMinutes" == 10

POST {{base_url}}/talks
{
  "title": "Two",
  "format": "LIGHTNING_20",
  "level": "INTERMEDIATE",
  "speakerId": {{speaker_id}}
}
HTTP 201
[Captures]
talk_2: jsonpath "$.id"

POST {{base_url}}/talks
{
  "title": "Three",
  "format": "WORKSHOP",
  "level": "ADVANCED",
  "speakerId": {{speaker_id}}
}
HTTP 201

POST {{base_url}}/talks
{
  "title": "Four",
  "format": "PRESENTATION",
  "level": "BEGINNER",
  "speakerId": {{speaker_id}}
}
HTTP 409

# P9: only whole numbers 1-5 are scores, and reviewers are positive whole numbers
PUT {{base_url}}/talks/{{talk_1}}/ratings/1
{
  "score": 0
}
HTTP 400

PUT {{base_url}}/talks/{{talk_1}}/ratings/1
{
  "score": -1
}
HTTP 400

PUT {{base_url}}/talks/{{talk_1}}/ratings/1
{
  "score": 6
}
HTTP 400

PUT {{base_url}}/talks/{{talk_1}}/ratings/1
{
  "score": 2.5
}
HTTP 400

PUT {{base_url}}/talks/{{talk_1}}/ratings/1
{
  "score": null
}
HTTP 400

PUT {{base_url}}/talks/{{talk_1}}/ratings/1
{
  "score": "3"
}
HTTP 400

PUT {{base_url}}/talks/{{talk_1}}/ratings/0
{
  "score": 3
}
HTTP 400

PUT {{base_url}}/talks/{{talk_1}}/ratings/-7
{
  "score": 3
}
HTTP 400

PUT {{base_url}}/talks/999999999/ratings/1
{
  "score": 3
}
HTTP 404

# P3, P11: 4, 4, 4, 5 averages 4.25, shown as 4.3; ratedAtDisplay has an ordinary space before AM/PM
PUT {{base_url}}/talks/{{talk_1}}/ratings/1
{
  "score": 4
}
HTTP 200
[Asserts]
jsonpath "$.ratedAtDisplay" matches /^\d{1,2}\/\d{1,2}\/\d{2}, \d{1,2}:\d{2} [AP]M$/

PUT {{base_url}}/talks/{{talk_1}}/ratings/2
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{talk_1}}/ratings/3
{
  "score": 4
}
HTTP 200

PUT {{base_url}}/talks/{{talk_1}}/ratings/4
{
  "score": 5
}
HTTP 200

GET {{base_url}}/talks/{{talk_1}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == 4.3
jsonpath "$.ratingCount" == 4

GET {{base_url}}/talks/{{talk_2}}
HTTP 200
[Asserts]
jsonpath "$.averageRating" == null
jsonpath "$.ratingCount" == 0

# P10: sort only knows "rating", written exactly like that
GET {{base_url}}/talks
[Query]
sort: RATING
speakerId: {{speaker_id}}
HTTP 400

GET {{base_url}}/talks
[Query]
sort: id
speakerId: {{speaker_id}}
HTTP 400

GET {{base_url}}/talks
[Query]
sort: score
speakerId: {{speaker_id}}
HTTP 400

# P13: unknown ids are 404, non-numeric ids are 400
GET {{base_url}}/speakers/999999999
HTTP 404

GET {{base_url}}/talks/999999999
HTTP 404

GET {{base_url}}/speakers/999999999/talks
HTTP 404

GET {{base_url}}/speakers/abc
HTTP 400

GET {{base_url}}/talks/abc
HTTP 400

# P16: the legacy client sends comments and fields we don't know
POST {{base_url}}/speakers
Content-Type: application/json
```
{
  // from the legacy web client
  "name": "Barbara Liskov",
  "email": "barbara-{{newUuid}}@example.com",
  "twitter": "@bl"
}
```
HTTP 201
[Asserts]
jsonpath "$.name" == "Barbara Liskov"
jsonpath "$.bio" == null

# P15: the running versions are reported (the JUnit test checks they're the real ones)
GET {{base_url}}/actuator/info
HTTP 200
[Asserts]
jsonpath "$.versions.springBoot" exists
jsonpath "$.versions.springFramework" exists
jsonpath "$.versions.java" exists
