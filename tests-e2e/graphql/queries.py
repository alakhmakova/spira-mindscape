GOALS = """
query {
  goals {
    id title description confidence deadline createdAt updatedAt achievedAt
    progress
    reality { id actions { id text } obstacles { id text } }
    options { id text selected position }
    targets { id type title progress }
    resources { id type title }
    confidenceHistory { id confidence at }
  }
}
"""

GOAL_BY_ID = """
query GoalById($id: ID!) {
  goalById(id: $id) {
    id title description confidence deadline createdAt updatedAt achievedAt
    progress
    reality { id actions { id text } obstacles { id text } }
    options { id text selected position }
    targets { id type title progress }
    resources { id type title }
    confidenceHistory { id confidence at }
  }
}
"""

CREATE_GOAL = """
mutation CreateGoal($title: String!, $confidence: Int!, $description: String, $deadline: String) {
  createGoal(input: {
    title: $title
    confidence: $confidence
    description: $description
    deadline: $deadline
  }) {
    id title description confidence deadline createdAt updatedAt achievedAt
    progress
    reality { id actions { id } obstacles { id } }
    options { id }
    targets { id }
    resources { id }
    confidenceHistory { confidence at }
  }
}
"""

UPDATE_GOAL = """
mutation UpdateGoal($id: ID!, $title: String, $confidence: Int, $description: String, $deadline: String, $achievedAt: String) {
  updateGoal(id: $id, input: {
    title: $title
    confidence: $confidence
    description: $description
    deadline: $deadline
    achievedAt: $achievedAt
  }) {
    id title description confidence deadline updatedAt achievedAt
    confidenceHistory { confidence at }
  }
}
"""

DELETE_GOAL = """
mutation DeleteGoal($id: ID!) {
  deleteGoal(id: $id)
}
"""

ADD_REALITY_ITEM = """
mutation AddRealityItem($goalId: ID!, $kind: String!, $text: String!) {
  addRealityItem(goalId: $goalId, kind: $kind, text: $text) {
    actions { id text createdAt updatedAt }
    obstacles { id text createdAt updatedAt }
  }
}
"""

UPDATE_REALITY_ITEM = """
mutation UpdateRealityItem($goalId: ID!, $kind: String!, $itemId: ID!, $text: String!) {
  updateRealityItem(goalId: $goalId, kind: $kind, itemId: $itemId, text: $text) {
    actions { id text }
    obstacles { id text }
  }
}
"""

REMOVE_REALITY_ITEM = """
mutation RemoveRealityItem($goalId: ID!, $kind: String!, $itemId: ID!) {
  removeRealityItem(goalId: $goalId, kind: $kind, itemId: $itemId) {
    actions { id text }
    obstacles { id text }
  }
}
"""

REALITY_BY_GOAL = """
query RealityByGoal($goalId: ID!) {
  realityByGoal(goalId: $goalId) {
    actions { id text }
    obstacles { id text }
  }
}
"""

REALITY_ITEM_BY_ID = """
query RealityItemById($id: ID!) {
  realityItemById(id: $id) {
    id text createdAt updatedAt
  }
}
"""

ADD_OPTION = """
mutation AddOption($goalId: ID!, $text: String!) {
  addOption(goalId: $goalId, text: $text) {
    id text selected position createdAt updatedAt
  }
}
"""

UPDATE_OPTION = """
mutation UpdateOption($goalId: ID!, $optionId: ID!, $text: String, $selected: Boolean) {
  updateOption(goalId: $goalId, optionId: $optionId, input: { text: $text, selected: $selected }) {
    id text selected position
  }
}
"""

SELECT_OPTION = """
mutation SelectOption($goalId: ID!, $optionId: ID!) {
  selectOption(goalId: $goalId, optionId: $optionId) {
    id selected
  }
}
"""

REMOVE_OPTION = """
mutation RemoveOption($goalId: ID!, $optionId: ID!) {
  removeOption(goalId: $goalId, optionId: $optionId)
}
"""

OPTIONS_BY_GOAL = """
query OptionsByGoal($goalId: ID!) {
  optionsByGoal(goalId: $goalId) {
    id text selected position
  }
}
"""

CREATE_TARGET = """
mutation CreateTarget($goalId: ID!, $type: String!, $title: String!, $start: Float, $total: Float, $unit: String, $deadline: String) {
  createTarget(goalId: $goalId, input: {
    type: $type
    title: $title
    start: $start
    total: $total
    unit: $unit
    deadline: $deadline
  }) {
    id type title start current total unit done progress deadline createdAt updatedAt
    items { id text done }
  }
}
"""

UPDATE_TARGET = """
mutation UpdateTarget($id: ID!, $done: Boolean, $current: Float, $deadline: String) {
  updateTarget(id: $id, input: { done: $done, current: $current, deadline: $deadline }) {
    id type title done current progress deadline updatedAt
  }
}
"""

DELETE_TARGET = """
mutation DeleteTarget($id: ID!) {
  deleteTarget(id: $id)
}
"""

TARGETS_BY_GOAL = """
query TargetsByGoal($goalId: ID!) {
  targetsByGoal(goalId: $goalId) {
    id type title progress done start current total unit
    items { id text done }
  }
}
"""

TARGET_BY_ID = """
query TargetById($id: ID!) {
  targetById(id: $id) {
    id type title progress done start current total unit deadline
    items { id text done }
  }
}
"""

CREATE_RESOURCE = """
mutation CreateResource($goalId: ID!, $type: String!, $title: String, $body: String, $url: String) {
  createResource(goalId: $goalId, input: { type: $type, title: $title, body: $body, url: $url }) {
    id type title body url createdAt updatedAt
  }
}
"""

UPDATE_RESOURCE = """
mutation UpdateResource($id: ID!, $title: String, $body: String, $url: String) {
  updateResource(id: $id, input: { title: $title, body: $body, url: $url }) {
    id type title body url updatedAt
  }
}
"""

DELETE_RESOURCE = """
mutation DeleteResource($id: ID!) {
  deleteResource(id: $id)
}
"""

RESOURCES_BY_GOAL = """
query ResourcesByGoal($goalId: ID!) {
  resourcesByGoal(goalId: $goalId) {
    id type title body url
  }
}
"""

RESOURCE_BY_ID = """
query ResourceById($id: ID!) {
  resourceById(id: $id) {
    id type title body url
  }
}
"""
