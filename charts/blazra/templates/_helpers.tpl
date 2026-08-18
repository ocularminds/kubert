{{- define "blazra.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "blazra.fullname" -}}
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

{{- define "blazra.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "blazra.selectorLabels" -}}
app.kubernetes.io/name: {{ include "blazra.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "blazra.labels" -}}
helm.sh/chart: {{ include "blazra.chart" . }}
{{ include "blazra.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "blazra.podLabels" -}}
{{- $required := include "blazra.selectorLabels" . | fromYaml }}
{{- toYaml (mergeOverwrite (deepCopy .Values.podLabels) $required) }}
{{- end }}

{{- define "blazra.podAnnotations" -}}
{{- $required := dict "kubectl.kubernetes.io/default-container" .Values.workload.containerName }}
{{- toYaml (mergeOverwrite (deepCopy .Values.podAnnotations) $required) }}
{{- end }}

{{- define "blazra.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "blazra.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- required "serviceAccount.name is required when serviceAccount.create is false" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "blazra.workloadImage" -}}
{{- if .Values.workload.image.digest }}
{{- printf "%s@%s" .Values.workload.image.repository .Values.workload.image.digest }}
{{- else }}
{{- printf "%s:%s" .Values.workload.image.repository .Values.workload.image.tag }}
{{- end }}
{{- end }}

{{- define "blazra.sidecarImage" -}}
{{- if .Values.blazra.image.digest }}
{{- printf "%s@%s" .Values.blazra.image.repository .Values.blazra.image.digest }}
{{- else }}
{{- $tag := default .Chart.AppVersion .Values.blazra.image.tag }}
{{- printf "%s:%s" .Values.blazra.image.repository $tag }}
{{- end }}
{{- end }}
