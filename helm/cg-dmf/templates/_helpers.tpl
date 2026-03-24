{{/*
Expand the name of the chart.
*/}}
{{- define "cg-dmf.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
Truncates at 63 chars because some Kubernetes name fields are limited.
*/}}
{{- define "cg-dmf.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart label value (name-version).
*/}}
{{- define "cg-dmf.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "cg-dmf.labels" -}}
helm.sh/chart: {{ include "cg-dmf.chart" . }}
{{ include "cg-dmf.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels (used in matchLabels and service selector).
*/}}
{{- define "cg-dmf.selectorLabels" -}}
app.kubernetes.io/name: {{ include "cg-dmf.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
ServiceAccount name to use.
*/}}
{{- define "cg-dmf.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "cg-dmf.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Name of the Secret that holds database credentials.
Set settings.database.existingSecret to the name of a pre-existing Secret to use it;
leave empty/null for the chart to create one named <fullname>-database.
*/}}
{{- define "cg-dmf.databaseSecretName" -}}
{{- .Values.settings.database.existingSecret | default (printf "%s-database" (include "cg-dmf.fullname" .)) }}
{{- end }}

{{/*
Name of the Secret that holds S3/MinIO credentials.
Set settings.s3.existingSecret to the name of a pre-existing Secret to use it;
leave empty/null for the chart to create one named <fullname>-s3.
*/}}
{{- define "cg-dmf.s3SecretName" -}}
{{- .Values.settings.s3.existingSecret | default (printf "%s-s3" (include "cg-dmf.fullname" .)) }}
{{- end }}

{{/*
Name of the Secret that holds OpenZaak credentials.
Set settings.openzaak.existingSecret to the name of a pre-existing Secret to use it;
leave empty/null for the chart to create one named <fullname>-openzaak.
*/}}
{{- define "cg-dmf.openzaakSecretName" -}}
{{- .Values.settings.openzaak.existingSecret | default (printf "%s-openzaak" (include "cg-dmf.fullname" .)) }}
{{- end }}

{{/*
Renders a value that contains a template.
Usage:
  {{ include "cg-dmf.tplvalues.render" (dict "value" .Values.path.to.value "context" $) }}
*/}}
{{- define "cg-dmf.tplvalues.render" -}}
{{- if typeIs "string" .value }}
{{- tpl .value .context }}
{{- else }}
{{- tpl (.value | toYaml) .context }}
{{- end }}
{{- end }}
