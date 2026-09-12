# Producer MCP

Local stdio MCP (2025-11-25), launched by the operator's client. It opens no
network listener and uses the existing Git/Wrangler authentication. Access to
this local server grants the ability to deploy reviewed Producer posts.

Start with `kbb --backend sci --classpath scripts:src scripts/producer_mcp.cljk`.
Set `OPPAI_REPO` to the absolute, clean fans-oppai worktree. Preserve this worktree
while clients reference it. The classpath and script paths must be absolute when
the MCP client starts in a different directory.

- `producer_list_posts`: lists receipt state, title, profile, reviewed hash, URL.
- `producer_publish`: takes UTC `hour`, exact `sha256`, and `expected_version`.
  It only publishes a reviewed saved zero-price PNG. The receipt supplies title,
  model, prompt, tags and profile. No arbitrary file paths or shell commands are
  accepted. Changing a reviewed image requires a new review and expected hash.

Publishing checks a clean repository, latest main ancestry, image hash, and a
cross-process lock. It adds only the image and catalog entry, tests/builds,
pushes, and deploys under resource-guard with a live-version check. Repeated
calls reuse deployed/published receipts. Failed builds can resume from the
existing matching committed image. Uncertain deployment outcomes require
inspection before supplying a new expected version.

`deployed` is distinct from `published`: verify the public detail and image
before setting the receipt to published. A deployed receipt does not block generation in the next hour; browser
verification is tracked separately. Browser access restrictions
must never be worked around using this server.

Verify locally with `kbb --backend sci --classpath scripts:src test/producer_publish_test.cljk`.

## Verify a post from MCP

Call `producer_verify_post` with `{"hour":"20260912T06Z"}`. This read-only tool
compares the receipt version with the current 100% Cloudflare deployment and
checks the committed PNG SHA256 and catalog metadata. It returns
`deployment_verified`, `deployment_changed`, or `artifact_mismatch`, along with
individual checks and a timestamp. Invalid or unavailable evidence is a tool
error, never a successful verification.

This is control-plane and source-artifact evidence, not an HTTP fetch of the
public image or browser rendering proof. Both `browser_verified` and
`public_image_fetch_verified` remain false. The tool does not modify receipts,
mark them published, generate images, or redeploy. After a subsequent deployment,
`deployment_changed` requires review even if that release may retain the post.

2026-09-12 operator update: Browser verification is non-blocking for hourly
creation. Keep deployed receipts as deployed (not browser-verified/published),
run producer_verify_post to report deployment/artifact evidence, and proceed
with the next hour's free generation. Generated, submitting and uncertain
receipts still require recovery to prevent duplicate requests. Never work around
browser access controls. Free quota and uncertain-request limits still apply.
