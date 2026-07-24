# AI Notes

## LLM (Large Language Model)

An LLM is an AI model trained on massive amounts of text to understand
and generate natural language. Examples: GPT, Claude, Gemini, Llama.

## Models

A model is the trained AI that performs tasks such as answering
questions, writing code, or summarizing text.

## Tools

Tools allow an LLM to interact with external systems such as databases,
web search, APIs, files, and email.

## Prompt

A prompt is the instruction given to the AI. Example:

``` text
Summarize this document in three bullet points.
```

## RAG

Retrieval-Augmented Generation retrieves relevant documents before the
LLM answers.

Flow:

``` text
Question -> Vector Search -> Relevant Documents -> LLM -> Answer
```

## Vectors (Embeddings)

Embeddings convert text into numeric vectors. Similar meanings produce
vectors that are close together, enabling semantic search.

## Fine-Tuning

Fine-tuning retrains an existing model using additional examples to
improve behavior on specific tasks.

## RAG vs Fine-Tuning

RAG                       Fine-Tuning
  ------------------------- ----------------------------
Uses external knowledge   Changes model weights
Easy to update            Requires retraining
Best for changing facts   Best for changing behavior
Uses vector search        Uses additional training

**Rule:** RAG teaches with documents. Fine-tuning teaches the model.

## MCP Server

Model Context Protocol (MCP) is a standard that lets AI models use
external tools and resources.

Architecture:

``` text
AI Client
   |
MCP Client
   |
MCP Server
   |
Tools / APIs / Databases
```

The MCP server exposes tools, prompts, and resources that AI clients can
discover and invoke.