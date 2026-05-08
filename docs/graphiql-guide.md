# GraphiQL Guide for Beginners

GraphiQL is a browser-based interface for working with GraphQL APIs. Think of it as Swagger UI
or Postman, but purpose-built for GraphQL. Open it at:

```
http://localhost:8080/graphiql.html
```

---

## How GraphQL differs from REST

In REST, each resource has its own URL: `GET /goals`, `POST /goals`, `GET /goals/{id}`.

In GraphQL there is **one endpoint** `/graphql`, and you describe exactly what you want in the
request body. There are two types of operations:

- **query** — read data (like GET)
- **mutation** — create, update, or delete data (like POST / PUT / DELETE)

---

## The GraphiQL interface

```
┌─────────────────────────┬──────────────────────────┐
│                         │                          │
│   Query editor          │   Result                 │
│   (write here)          │   (JSON response)        │
│                         │                          │
├─────────────────────────┤                          │
│   Variables             │                          │
│   (JSON variables)      │                          │
├─────────────────────────┤                          │
│   Headers               │                          │
│   (request headers)     │                          │
└─────────────────────────┴──────────────────────────┘
```

**▶ Play button** — execute the query.  
**Prettify button** — auto-format the query.  
**Docs button** (top right) — schema documentation, auto-generated from your code.

---

## First query — fetch all goals

Paste this into the editor and press ▶:

```graphql
query {
  goals {
    id
    title
    description
    confidence
    progress
  }
}
```

If the database is empty you will get `"goals": []`. That is expected.

---

## Create a goal (mutation)

```graphql
mutation {
  createGoal(input: {
    title: "Learn GraphQL"
    description: "Understand queries, mutations, and schema"
    confidence: 8
  }) {
    id
    title
    createdAt
  }
}
```

The response will include an `id` — save it for the next requests.

---

## Fetch a specific goal by id

```graphql
query {
  goalById(id: "2") {
    id
    title
    description
    confidence
    progress
    targets {
      id
      title
      type
    }
    resources {
      id
      title
      type
    }
  }
}
```

In GraphQL you choose which fields to return — unlike REST where the server decides what to send.

---

## Using variables (like Postman parameters)

Instead of hardcoding values directly in the query, use variables.

**Query** (top editor panel):

```graphql
mutation CreateGoal($input: CreateGoalInput!) {
  createGoal(input: $input) {
    id
    title
    createdAt
  }
}
```

**Variables** (bottom panel, Variables tab):

```json
{
  "input": {
    "title": "New goal",
    "description": "Description",
    "confidence": 7
  }
}
```

This is useful when you want to test the same operation with different data.

---

## Schema documentation (like Swagger)

Click the **Docs** button in the top right corner of GraphiQL. A side panel opens with:

- all available **queries** (what you can read)
- all available **mutations** (what you can change)
- data types and their fields

This is auto-generated from the GraphQL schema file located at:

```
backend/src/main/resources/graphql/schema.graphqls
```

---

## Comparison with Postman

| Postman | GraphiQL |
|---|---|
| Method + URL | `query` or `mutation` |
| Body (JSON) | query body in the editor |
| Params / Variables | Variables tab |
| Headers | Headers tab |
| Collections | not available (queries are not saved) |
| Response | right panel |

**The key difference from Postman:** in GraphQL you describe the *shape* of the response — you
specify only the fields you need. The server will not return anything extra.

---

## Typical workflow during development

1. Open `http://localhost:8080/graphiql.html`
2. Click **Docs** to explore available operations
3. Write a mutation to create test data
4. Write a query to verify the data was saved correctly
5. If something is wrong — check the backend logs in the terminal

---

## Full Spira API smoke test

Run these in order to verify everything works:

**1. Create a goal:**
```graphql
mutation {
  createGoal(input: {
    title: "Test goal"
    description: "Checking the GraphQL API"
    confidence: 7
  }) {
    id
    title
  }
}
```

**2. Copy the `id` from the response and paste it into this query:**
```graphql
query {
  goalById(id: "PASTE_ID_HERE") {
    id
    title
    description
    confidence
    progress
  }
}
```

**3. Fetch all goals:**
```graphql
query {
  goals {
    id
    title
    confidence
    progress
  }
}
```

If all three return data without errors — the API is working correctly.
