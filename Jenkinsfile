/*
 * While this is not a plugin, it is much simpler to reuse the pipeline code for CI. This allows for
 * easy Linux/Windows testing and produces incrementals. The only feature that relates to plugins is
 * allowing one to test against multiple Jenkins versions.
 */
buildPlugin(useContainerAgent: false, configurations: [
  // TODO: switch to 2022 or 2025
  // 2019 agents won't be available for long cf https://github.com/jenkins-infra/helpdesk/issues/4954
  [platform: 'windows-2019', jdk: 25],
  [platform: 'windows-2019', jdk: 21]
])

