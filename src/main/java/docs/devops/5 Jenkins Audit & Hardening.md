Description

Story 5.1 — Inspect current Jenkins on the dev/qa/prod VMs
Subtask 5.1.1 Collect: Jenkins version, JVM heap (-Xmx), plugin list (Manage Jenkins → Plugins), executors count, build queue history, disk usage /var/lib/jenkins.

Subtask 5.1.2 Document topology. Recommendation for your scale (3 VMs, 1 backend, ~5–20 builds/day):

Use a single Jenkins controller + 1 dedicated build agent (agent on a separate VM or as a K8s pod via kubernetes-plugin).

Do not run builds on the controller (set executors=0 on controller).

Move to master/slave (controller/agent) when you hit any of:

10 concurrent builds, or

Build queue wait > 2 min during peak, or

Controller CPU > 70 % sustained, or

Heap > 4 GB.

Subtask 5.1.3 Sizing baseline:

Controller: 2 vCPU / 4 GB RAM / 100 GB SSD, -Xmx2g.

Agent: 4 vCPU / 8 GB RAM (Maven + Docker build are heavy).

Subtask 5.1.4 Security: enable matrix auth, disable anonymous, integrate with LDAP/SSO, store creds only in Jenkins credentials store, enable CSRF + agent-to-controller access control.

Subtask 5.1.5 Backup JENKINS_HOME nightly (exclude workspace/, builds/*/archive).

Acceptance: Documented inventory + sizing decision + green pipeline run on agent.