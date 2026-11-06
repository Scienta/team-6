# GET /speakers/search?q= (admin, not in any spec). q used to be pasted into the SQL: SQL injection.
# Now a bind parameter, with LIKE wildcards in q escaped.

# Setup: one speaker with a unique name, one with a % in it
POST {{base_url}}/speakers
{ "name": "Searchable {{newUuid}}", "email": "search-{{newUuid}}@example.com" }
HTTP 201
[Captures]
name: jsonpath "$.name"

POST {{base_url}}/speakers
{ "name": "Pct 100%-{{newUuid}}", "email": "pct-{{newUuid}}@example.com" }
HTTP 201
[Captures]
pct_name: jsonpath "$.name"

# A normal search finds exactly that speaker
GET {{base_url}}/speakers/search
[QueryStringParams]
q: {{name}}
HTTP 200
[Asserts]
jsonpath "$" count == 1
jsonpath "$[0].name" == "{{name}}"

# Injection: used to return every speaker ('%' OR 1=1 --); now it's just text nobody's name contains
GET {{base_url}}/speakers/search
[QueryStringParams]
q: ' OR 1=1 --
HTTP 200
[Asserts]
jsonpath "$" count == 0

# A lone quote used to be a SQL syntax error (500)
GET {{base_url}}/speakers/search
[QueryStringParams]
q: '
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Stacked statement: harmless text now
GET {{base_url}}/speakers/search
[QueryStringParams]
q: x'; DROP TABLE speaker; --
HTTP 200
[Asserts]
jsonpath "$" count == 0

# The speaker table is still there
POST {{base_url}}/speakers
{ "name": "Still Here", "email": "still-{{newUuid}}@example.com" }
HTTP 201

# % in the search text is a literal %, not a wildcard
GET {{base_url}}/speakers/search
[QueryStringParams]
q: {{pct_name}}
HTTP 200
[Asserts]
jsonpath "$" count == 1
jsonpath "$[0].name" == "{{pct_name}}"
