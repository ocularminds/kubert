{{- define "kubert.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "kubert.fullname" -}}
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

{{- define "kubert.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "kubert.selectorLabels" -}}
app.kubernetes.io/name: {{ include "kubert.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "kubert.labels" -}}
helm.sh/chart: {{ include "kubert.chart" . }}
{{ include "kubert.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "kubert.podLabels" -}}
{{- $required := include "kubert.selectorLabels" . | fromYaml }}
{{- toYaml (mergeOverwrite (deepCopy .Values.podLabels) $required) }}
{{- end }}

{{- define "kubert.podAnnotations" -}}
{{- $required := dict "kubectl.kubernetes.io/default-container" .Values.workload.containerName }}
{{- toYaml (mergeOverwrite (deepCopy .Values.podAnnotations) $required) }}
{{- end }}

{{- define "kubert.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "kubert.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- required "serviceAccount.name is required when serviceAccount.create is false" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "kubert.workloadImage" -}}
{{- if .Values.workload.image.digest }}
{{- printf "%s@%s" .Values.workload.image.repository .Values.workload.image.digest }}
{{- else }}
{{- printf "%s:%s" .Values.workload.image.repository .Values.workload.image.tag }}
{{- end }}
{{- end }}

{{- define "kubert.sidecarImage" -}}
{{- if .Values.kubert.image.digest }}
{{- printf "%s@%s" .Values.kubert.image.repository .Values.kubert.image.digest }}
{{- else }}
{{- $tag := default .Chart.AppVersion .Values.kubert.image.tag }}
{{- printf "%s:%s" .Values.kubert.image.repository $tag }}
{{- end }}
{{- end }}
