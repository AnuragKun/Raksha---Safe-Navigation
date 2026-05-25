---
name: Google Stitch App Design & Redesign
description: Skill set for utilizing Google Stitch MCP tools to design, redesign, and theme applications.
---

# Google Stitch App Design & Redesign Skill

This skill provides comprehensive instructions on how to leverage the Stitch MCP server to design new screens, redesign existing ones, and manage unifying design systems.

## Prerequisites
- You must have the `StitchMCP` commands available (tools starting with `mcp_StitchMCP_`).
- Be aware that screen generation/editing actions can take a few minutes. **DO NOT RETRY** if you encounter timeout errors. If it fails due to a connection error, try fetching the result using `get_project` or `get_screen` after a while.

## 1. Creating a New Project (Design from Scratch)
**Tool**: `mcp_StitchMCP_create_project`
When a user wants to build an app from scratch:
1. Call `mcp_StitchMCP_create_project` to get a new `projectId`.
2. Use this `projectId` for all subsequent operations involving this app.

## 2. Generating a New Screen
**Tool**: `mcp_StitchMCP_generate_screen_from_text`
Create a base component by describing it in the `prompt`. 
1. Supply the `projectId` and `prompt` (e.g., "A modern login screen with glassmorphism").
2. Optional params: `deviceType` (MOBILE, DESKTOP, TABLET) and `modelId` (e.g., GEMINI_3_PRO).
3. If `output_components` contains suggestions, present them to the user. Ask the user for approval, and upon accepting, invoke this tool again with the accepted suggestion.

## 3. Editing/Redesigning Screens
**Tool**: `mcp_StitchMCP_edit_screens`
When a user asks to "redesign" or "update" an existing screen:
1. If you do not have the screen ID, list them using `mcp_StitchMCP_list_screens` and finding the appropriate one with `mcp_StitchMCP_get_screen`.
2. Call `mcp_StitchMCP_edit_screens` providing the `projectId`, `selectedScreenIds`, and the `prompt` detailing the changes (e.g., "Change background to dark mode, make the buttons circular").

## 4. Exploring Design Alternatives (Variants)
**Tool**: `mcp_StitchMCP_generate_variants`
When a user wants to see different options for a screen:
1. Provide the `projectId`, `selectedScreenIds`, and `prompt` explaining what variants you want to explore.
2. Provide `variantOptions` specifying how many variants and what range of creativity is expected.

## 5. Unified Design Systems (Theming)
Use design systems to quickly and globally sync branding across all screens.

**A. Create Design System:** `mcp_StitchMCP_create_design_system`
1. First step when a new theme needs to be established. Define colors, typography, shapes, etc., in `designSystem`. Provide `projectId` to link it to the app.
2. **Crucial Next Step**: Immediately call `mcp_StitchMCP_update_design_system` to apply the initial design system, so it correctly displays in the UI.

**B. Update Existing Design System:** `mcp_StitchMCP_update_design_system`
1. List design systems (`mcp_StitchMCP_list_design_systems`) to find the `assetId`.
2. Call `mcp_StitchMCP_update_design_system` using the `name` (e.g. `assets/123...`) and the updated configuration.

**C. Apply Design System to Screens:** `mcp_StitchMCP_apply_design_system`
1. If the goal is only to update matching token styles on specific screens, use `mcp_StitchMCP_apply_design_system`. Give it the `projectId`, `selectedScreenInstances`, and the `assetId`.

## General Best Practices
- **Patience**: Visual generation takes time. Wait for operations to complete, or poll.
- **Granular Edits**: Focus text prompts for edits on specific, actionable visual changes (e.g. "change header text color to white", "add a rounded shadow to the card").
- **Review**: Always ask the user for feedback after rendering a screen. Present them with options or suggestions when generated.
