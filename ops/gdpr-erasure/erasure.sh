#!/usr/bin/env bash
# Erase one person's data from Snap2Snomed logs and backups (GDPR Article 17).
# See README.md in this folder for the process. Run `erasure.sh help` for commands.
#
# Two local folders per request:
#   STATE    ~/.gdpr-erasure/<ref>   holds the person's identifiers. Deleted by `cleanup`.
#   EVIDENCE ~/gdpr-evidence/<ref>   holds no personal data. File it in the records system.
#
# Every command that deletes or rewrites data does a dry run unless you pass --yes.
# Works with the macOS bash 3.2 as well as bash 5.

set -euo pipefail

APP="${S2S_APP_NAME:-snap2snomed-app}"
REGION="${AWS_REGION:-eu-central-1}"
BUCKET="${S2S_BACKUP_BUCKET:-snap2snomed-backups}"
EXPORT_PREFIX="cognito/"
RDS_CLUSTER="${S2S_RDS_CLUSTER:-api-$APP}"
LOKI_CONTEXT="${LOKI_KUBE_CONTEXT:-ontoserver-dev-k8s}"
LOKI_NS="${LOKI_NAMESPACE:-monitoring}"
LOKI_SELECTOR="{__aws_cloudwatch_log_group=~\".*$APP.*\"}"
BT='`'   # LogQL raw-string quote, so escaped regex characters reach Loki unchanged
STATE_ROOT="${GDPR_STATE_ROOT:-$HOME/.gdpr-erasure}"
EVIDENCE_ROOT="${GDPR_EVIDENCE_ROOT:-$HOME/gdpr-evidence}"
LOGS_START=1609459200   # 2021-01-01, before Snap2Snomed went live

export AWS_REGION="$REGION"
umask 077

die()  { echo "error: $*" >&2; exit 1; }
info() { echo "$*" >&2; }
need() { for c in "$@"; do command -v "$c" >/dev/null || die "needs '$c' on the PATH"; done; }

# ---------- request state ----------

current_ref() {
  [ -f "$STATE_ROOT/current" ] || die "no active request; run: erasure.sh init <request-ref> <email>"
  cat "$STATE_ROOT/current"
}

load() {
  REF="$(current_ref)"
  STATE="$STATE_ROOT/$REF"
  EVIDENCE="$EVIDENCE_ROOT/$REF"
  mkdir -p "$EVIDENCE"
  [ -f "$STATE/subject.env" ] || die "missing $STATE/subject.env; run init again"
  # shellcheck disable=SC1091
  . "$STATE/subject.env"
  # A short identifier matches unrelated log lines, and cw-delete would then delete the wrong streams.
  for v in "$EMAIL" "$SUB" "$SUBJECT_USERNAME" "$IDP_ID"; do
    [ "${#v}" -ge 6 ] || die "identifier '$v' is shorter than 6 characters; fix $STATE/subject.env"
  done
  PAT="$(re_escape "$EMAIL")|$(re_escape "$SUB")|$(re_escape "$SUBJECT_USERNAME")|$(re_escape "$IDP_ID")"
}

# Escape regex characters so an email like a.b+c@x.org matches only itself.
re_escape() { printf '%s' "$1" | sed 's#[][\.*^$+?(){}|/]#\\&#g'; }

sha() { printf '%s' "$1" | shasum -a 256 | cut -c1-16; }

actor() {
  if [ -z "${ACTOR:-}" ]; then
    ACTOR="$(aws sts get-caller-identity --query Arn --output text 2>/dev/null || echo unknown)"
  fi
  echo "$ACTOR"
}

# evidence <action> <json-object-of-details>
# Appends one JSON line. Never pass personal data in the details.
evidence() {
  mkdir -p "$EVIDENCE"
  jq -cn --arg ts "$(date -u +%FT%TZ)" --arg ref "$REF" --arg actor "$(actor)" \
    --arg subject "$SUBJECT_REF" --arg action "$1" --argjson details "$2" \
    '{ts: $ts, request: $ref, actor: $actor, subject_ref: $subject, action: $action} + $details' \
    >> "$EVIDENCE/evidence.jsonl"
}

require_yes() {
  case " $* " in *" --yes "*) return 0 ;; esac
  return 1
}

require_legal_clearance() {
  [ -f "$STATE/legal-hold-cleared" ] || die "legal-hold gate not passed; run: erasure.sh legal-hold cleared \"<reference>\""
}

# ---------- init ----------

cmd_init() {
  [ $# -ge 2 ] || die "usage: erasure.sh init <request-ref> <email>"
  need aws jq shasum
  REF="$1"; EMAIL="$2"
  case "$REF" in *[!A-Za-z0-9._-]*) die "request-ref may use letters, digits, . _ - only" ;; esac
  STATE="$STATE_ROOT/$REF"; EVIDENCE="$EVIDENCE_ROOT/$REF"
  mkdir -p "$STATE" "$EVIDENCE"; chmod 700 "$STATE_ROOT" "$STATE"

  pool="$(aws cognito-idp list-user-pools --max-results 60 \
    --query "UserPools[?Name=='$APP'].Id | [0]" --output text)"
  [ "$pool" != "None" ] || die "no user pool named $APP in $REGION"

  aws cognito-idp list-users --user-pool-id "$pool" --filter "email = \"$EMAIL\"" \
    --output json > "$STATE/subject.json"
  n="$(jq '.Users | length' "$STATE/subject.json")"
  source_of_ids="cognito"
  if [ "$n" = 0 ]; then
    info "Not in Cognito. Looking in the newest user-pool export in s3://$BUCKET/$EXPORT_PREFIX"
    key="$(aws s3api list-objects-v2 --bucket "$BUCKET" --prefix "$EXPORT_PREFIX" --output json |
      jq -r '.Contents | max_by(.LastModified) | .Key')"
    aws s3 cp "s3://$BUCKET/$key" - --quiet |
      jq --arg e "$EMAIL" '{Users: [.Users[] | select(any(.Attributes[]?; .Name=="email" and (.Value|ascii_downcase)==($e|ascii_downcase)))]}' \
      > "$STATE/subject.json"
    n="$(jq '.Users | length' "$STATE/subject.json")"
    source_of_ids="export:$key"
  fi
  [ "$n" = 1 ] || die "found $n accounts for that email; resolve by hand (see $STATE/subject.json)"

  attr() { jq -r --arg n "$1" '.Users[0].Attributes[] | select(.Name==$n) | .Value' "$STATE/subject.json"; }
  SUB="$(attr sub)"
  SUBJECT_USERNAME="$(jq -r '.Users[0].Username' "$STATE/subject.json")"
  IDP_ID="$(attr identities | jq -r '.[0].userId // empty' 2>/dev/null || true)"
  GIVEN="$(attr given_name)"; FAMILY="$(attr family_name)"
  [ -n "$SUB" ] && [ -n "$SUBJECT_USERNAME" ] || die "account has no sub or username"
  [ -n "$IDP_ID" ] || IDP_ID="$SUB"   # native accounts have no SSO ID
  SUBJECT_REF="$(sha "$SUB")"

  {
    printf 'EMAIL=%q\nSUB=%q\nSUBJECT_USERNAME=%q\nIDP_ID=%q\nGIVEN=%q\nFAMILY=%q\nSUBJECT_REF=%q\nPOOL=%q\n' \
      "$EMAIL" "$SUB" "$SUBJECT_USERNAME" "$IDP_ID" "$GIVEN" "$FAMILY" "$SUBJECT_REF" "$pool"
  } > "$STATE/subject.env"
  chmod 600 "$STATE/subject.env" "$STATE/subject.json"
  echo "$REF" > "$STATE_ROOT/current"

  variants="$(aws cognito-idp list-users --user-pool-id "$pool" \
    --filter "email ^= \"${EMAIL%%@*}\"" --query 'length(Users)' --output text)"
  evidence init "$(jq -cn --arg s "$source_of_ids" --argjson v "$variants" \
    '{identifiers_from: $s, cognito_accounts_with_same_local_part: $v}')"

  info "Request $REF is active. Subject reference: $SUBJECT_REF"
  info "Accounts whose email starts with the same local part: $variants (check these by hand if more than 1)"
}

# ---------- legal-hold gate ----------

cmd_legal_hold() {
  [ $# -ge 2 ] || die "usage: erasure.sh legal-hold cleared|hold \"<reference, no personal data>\""
  load
  case "$1" in
    cleared)
      echo "$2" > "$STATE/legal-hold-cleared"
      evidence legal_hold_cleared "$(jq -cn --arg r "$2" '{reference: $r}')"
      info "Gate passed. Destructive commands are now allowed." ;;
    hold)
      rm -f "$STATE/legal-hold-cleared"
      evidence legal_hold_in_place "$(jq -cn --arg r "$2" '{reference: $r}')"
      info "Hold recorded. Do not delete anything. Restrict the data and involve legal." ;;
    *) die "first argument must be 'cleared' or 'hold'" ;;
  esac
}

# ---------- CloudWatch ----------

log_groups() {
  aws logs describe-log-groups --log-group-name-pattern "$APP" \
    --query 'logGroups[].logGroupName' --output text | tr '\t' '\n'
}

# insights <group> <regex>  -> TSV: group, stream, hits
insights() {
  local id status
  id="$(aws logs start-query --log-group-name "$1" --start-time "$LOGS_START" --end-time "$(date +%s)" \
    --query-string "filter @message like /(?i)($2)/ | stats count() as hits by @logStream | limit 10000" \
    --query queryId --output text)"
  while :; do
    status="$(aws logs get-query-results --query-id "$id" --query status --output text)"
    case "$status" in Complete) break ;; Failed|Cancelled|Timeout) die "query on $1 ended: $status" ;; esac
    sleep 10
  done
  aws logs get-query-results --query-id "$id" --output json |
    jq -r --arg g "$1" '.results[] | map({(.field): .value}) | add | [$g, .["@logStream"], .hits] | @tsv'
}

cmd_cw_search() {
  load; need aws jq
  local out regex label groups
  if [ "${1:-}" = "--names" ]; then
    g="$(re_escape "$GIVEN")"; f="$(re_escape "$FAMILY")"
    regex="$g.{0,3}$f|$f.{0,3}$g"; out="$STATE/name-streams.tsv"; label=names
  else
    regex="$PAT"; out="$STATE/streams.tsv"; label=ids
  fi
  : > "$out"
  # Capture lists before looping: a failed AWS call then stops the script instead of looking like "no matches".
  groups="$(log_groups)"
  [ -n "$groups" ] || die "no log groups match $APP in $REGION"
  for g in $groups; do
    info "searching $g"
    insights "$g" "$regex" >> "$out"
  done
  summary="$(jq -Rn '[inputs | split("\t") | {group: .[0], stream: .[1], hits: (.[2]|tonumber)}]
    | group_by(.group) | map({group: .[0].group, streams: length, hits: (map(.hits)|add)})' < "$out")"
  cut -f1,2 "$out" > "$EVIDENCE/cloudwatch-$label-streams-$(date -u +%Y%m%dT%H%M%SZ).tsv"
  evidence "cloudwatch_search_$label" "$(jq -cn --argjson s "$summary" '{results: $s}')"
  echo "$summary" | jq -r '.[] | "\(.group)\t\(.streams) streams\t\(.hits) lines"'
  [ "$label" = ids ] || info "Name matches can be false positives. Review $out and append confirmed rows to $STATE/streams.tsv"
}

cmd_cw_delete() {
  load; need aws
  local list="$STATE/streams.tsv"
  [ -s "$list" ] || die "nothing to delete; run cw-search first"
  if ! require_yes "$@"; then
    info "Dry run. These streams would be deleted (whole streams; other lines in them go too):"
    cut -f1,2 "$list" | sort -u
    info "Run again with --yes to delete."
    return
  fi
  require_legal_clearance
  cut -f1,2 "$list" | sort -u | while IFS="$(printf '\t')" read -r g s; do
    if aws logs delete-log-stream --log-group-name "$g" --log-stream-name "$s" 2>/dev/null; then r=deleted; else r=failed; fi
    evidence cloudwatch_delete_stream "$(jq -cn --arg g "$g" --arg s "$s" --arg r "$r" '{group: $g, stream: $s, result: $r}')"
    echo "$r $g $s"
  done
}

# ---------- Loki ----------

loki_get() {   # loki_get <service> <path-with-query>
  kubectl --context "$LOKI_CONTEXT" get --raw "/api/v1/namespaces/$LOKI_NS/services/$1:3100/proxy$2"
}

uri() { jq -rn --arg s "$1" '$s|@uri'; }

cmd_loki_search() {
  load; need kubectl jq
  q="sum by (__aws_cloudwatch_log_group) (count_over_time($LOKI_SELECTOR |~ ${BT}(?i)($PAT)${BT} [365d]))"
  result="$(loki_get loki-read "/loki/api/v1/query?query=$(uri "$q")" | jq -c '[.data.result[] | {group: .metric.__aws_cloudwatch_log_group, lines: (.value[1]|tonumber)}]')"
  oldest="$(loki_get loki-read "/loki/api/v1/query_range?query=$(uri "$LOKI_SELECTOR")&start=$(( $(date +%s) - 400*86400 ))000000000&limit=1&direction=forward" |
    jq -r '.data.result[0].values[0][0] // empty' | cut -c1-10)"
  evidence loki_search "$(jq -cn --argjson r "$result" --arg o "$oldest" '{results: $r, oldest_line_epoch: $o}')"
  echo "$result" | jq -r 'if length == 0 then "no matches" else .[] | "\(.group)\t\(.lines) lines" end'
}

cmd_loki_delete() {
  load; need kubectl curl jq
  q="$LOKI_SELECTOR |~ ${BT}(?i)($PAT)${BT}"
  if ! require_yes "$@"; then
    info "Dry run. Would file a Loki delete request for the last 366 days. Run again with --yes."
    return
  fi
  require_legal_clearance
  kubectl --context "$LOKI_CONTEXT" -n "$LOKI_NS" port-forward svc/loki-backend 31000:3100 >/dev/null 2>&1 &
  pf=$!; trap 'kill $pf 2>/dev/null || true' EXIT; sleep 3
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST -G http://localhost:31000/loki/api/v1/delete \
    --data-urlencode "query=$q" --data-urlencode "start=$(( $(date +%s) - 366*86400 ))" --data-urlencode "end=$(date +%s)")"
  evidence loki_delete_request "$(jq -cn --arg c "$code" '{http_status: $c, note: "applied after the 24h cancel period"}')"
  [ "$code" = 204 ] || die "Loki returned HTTP $code"
  info "Delete request filed. Run loki-search again after 25 hours and expect no matches."
}

# ---------- Cognito exports in S3 ----------

exports() {   # exports <jq filter on .Contents[]> -> "key<TAB>class"
  aws s3api list-objects-v2 --bucket "$BUCKET" --prefix "$EXPORT_PREFIX" --output json |
    jq -r ".Contents[] | select($1) | [.Key, (.StorageClass // \"STANDARD\")] | @tsv"
}

contains_subject() { jq -e --arg s "$SUB" 'any(.Users[]; any(.Attributes[]?; .Name=="sub" and .Value==$s))' "$1" >/dev/null; }

cmd_s3_scan() {
  load; need aws jq
  local tmp found=0 checked=0 readable archived
  tmp="$STATE/export.json"
  : > "$STATE/exports-with-subject.txt"
  readable="$(exports '.StorageClass != "GLACIER" and .StorageClass != "DEEP_ARCHIVE"')"
  archived="$(exports '.StorageClass == "GLACIER" or .StorageClass == "DEEP_ARCHIVE"')"
  while IFS="$(printf '\t')" read -r k c; do
    [ -n "$k" ] || continue
    aws s3 cp "s3://$BUCKET/$k" "$tmp" --quiet
    checked=$((checked + 1))
    if contains_subject "$tmp"; then echo "$k" >> "$STATE/exports-with-subject.txt"; found=$((found + 1)); fi
  done <<< "$readable"
  rm -f "$tmp"
  glacier="$(printf '%s' "$archived" | grep -c . || true)"
  evidence s3_export_scan "$(jq -cn --argjson c "$checked" --argjson f "$found" --argjson g "$glacier" \
    '{readable_files_checked: $c, files_with_subject: $f, archived_files_not_checked: $g}')"
  echo "readable files checked: $checked, containing the person: $found, archived (not checked): $glacier"
}

cmd_s3_scrub() {
  load; need aws jq
  local list="$STATE/exports-with-subject.txt" in="$STATE/in.json" out="$STATE/out.json"
  [ -s "$list" ] || die "nothing to scrub; run s3-scan first"
  if ! require_yes "$@"; then
    info "Dry run. $(wc -l < "$list" | tr -d ' ') files would be rewritten without the person. Run again with --yes."
    return
  fi
  require_legal_clearance
  while read -r k; do
    c="$(aws s3api head-object --bucket "$BUCKET" --key "$k" --query 'StorageClass' --output text)"
    [ "$c" = None ] && c=STANDARD
    aws s3 cp "s3://$BUCKET/$k" "$in" --quiet
    jq --arg s "$SUB" '.Users |= map(select(all(.Attributes[]?; .Name!="sub" or .Value!=$s)))' "$in" > "$out"
    if contains_subject "$out"; then r=failed; else
      aws s3 cp "$out" "s3://$BUCKET/$k" --storage-class "$c" --quiet && r=rewritten || r=failed
    fi
    evidence s3_export_scrub "$(jq -cn --arg k "$k" --arg c "$c" --arg r "$r" '{key: $k, storage_class: $c, result: $r}')"
    echo "$r $k"
  done < "$list"
  rm -f "$in" "$out"
}

cmd_s3_restore() {
  load; need aws
  local n=0 archived
  archived="$(exports '.StorageClass == "GLACIER"')"
  while IFS="$(printf '\t')" read -r k _; do
    [ -n "$k" ] || continue
    aws s3api restore-object --bucket "$BUCKET" --key "$k" \
      --restore-request '{"Days":3,"GlacierJobParameters":{"Tier":"Bulk"}}' 2>/dev/null || true
    n=$((n + 1))
  done <<< "$archived"
  evidence s3_export_restore_requested "$(jq -cn --argjson n "$n" '{files: $n, tier: "Bulk", days: 3}')"
  info "Restore requested for $n files. Bulk restores take 5 to 12 hours; then run s3-scan and s3-scrub."
}

cmd_s3_delete_before() {
  [ $# -ge 1 ] || die "usage: erasure.sh s3-delete-before <YYYY-MM-DD> [--yes]"
  load; need aws jq
  local cutoff="$1"; shift
  case "$cutoff" in [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]) ;; *) die "date must be YYYY-MM-DD" ;; esac
  keys="$(exports ".LastModified < \"$cutoff\"" | cut -f1)"
  n="$(printf '%s\n' "$keys" | grep -c . || true)"
  if ! require_yes "$@"; then
    info "Dry run. $n exports older than $cutoff would be deleted. Run again with --yes."
    return
  fi
  require_legal_clearance
  printf '%s\n' "$keys" | while read -r k; do
    [ -n "$k" ] || continue
    aws s3 rm "s3://$BUCKET/$k" --quiet && r=deleted || r=failed
    evidence s3_export_delete "$(jq -cn --arg k "$k" --arg r "$r" '{key: $k, result: $r}')"
    echo "$r $k"
  done
}

# ---------- RDS ----------

cmd_rds_status() {
  load; need aws jq
  local created="${1:-}"
  retention="$(aws rds describe-db-clusters --db-cluster-identifier "$RDS_CLUSTER" --query 'DBClusters[0].BackupRetentionPeriod' --output text)"
  snaps="$(aws rds describe-db-cluster-snapshots --db-cluster-identifier "$RDS_CLUSTER" --output json |
    jq -c '[.DBClusterSnapshots[] | {id: .DBClusterSnapshotIdentifier, type: .SnapshotType, taken: .SnapshotCreateTime}]')"
  orphans="$(aws rds describe-db-cluster-snapshots --snapshot-type manual --output json |
    jq -c --arg c "$RDS_CLUSTER" '[.DBClusterSnapshots[] | select(.DBClusterIdentifier != $c) | {id: .DBClusterSnapshotIdentifier, cluster: .DBClusterIdentifier, taken: .SnapshotCreateTime}]')"
  evidence rds_status "$(jq -cn --argjson r "$retention" --argjson s "$snaps" --argjson o "$orphans" --arg c "$created" \
    '{retention_days: $r, snapshots: $s, manual_snapshots_of_other_clusters: $o, subject_created: $c}')"
  echo "automated backup retention: $retention days"
  jq -rn --argjson s "$snaps" --argjson o "$orphans" --arg c "$created" '
    ($s + $o)[] | "\(.taken[0:10])\t\(.type // "manual")\t\(.id)" +
    (if $c != "" and (.type // "manual") == "manual" then
       (if .taken[0:10] > $c then "\tMAY HOLD THE PERSON" else "\tbefore sign-up" end) else "" end)'
}

# ---------- verify and clean up ----------

cmd_verify() {
  load
  info "== CloudWatch"; cmd_cw_search
  info "== Loki";       cmd_loki_search
  info "== S3 exports"; cmd_s3_scan
  evidence verify_complete '{}'
}

cmd_cleanup() {
  load
  if ! require_yes "$@"; then
    info "Dry run. Would delete $STATE (the person's identifiers). Evidence in $EVIDENCE stays. Run again with --yes."
    return
  fi
  evidence cleanup '{"note": "local identifier files deleted"}'
  rm -rf "$STATE"; rm -f "$STATE_ROOT/current"
  info "Deleted $STATE. File the evidence folder: $EVIDENCE"
}

cmd_help() {
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  cat <<'EOF'

Commands, in the order you normally run them:
  init <request-ref> <email>        capture identifiers from Cognito (or the newest export)
  legal-hold cleared|hold "<ref>"   record the legal-hold answer; deletes need "cleared"
  cw-search [--names]               find CloudWatch streams that hold the person's IDs (or names)
  cw-delete [--yes]                 delete those streams
  loki-search                       count Loki lines that hold the person's IDs
  loki-delete [--yes]               file a Loki delete request
  s3-scan                           find user-pool exports that contain the person
  s3-scrub [--yes]                  rewrite those exports without the person
  s3-restore                        restore archived exports so they can be scanned
  s3-delete-before <date> [--yes]   delete exports older than a date
  rds-status [<sign-up date>]       list backup retention and snapshots
  verify                            run all searches again and record the results
  cleanup [--yes]                   delete the local identifier files
EOF
}

main() {
  local cmd="${1:-help}"; shift || true
  case "$cmd" in
    init) cmd_init "$@" ;;
    legal-hold) cmd_legal_hold "$@" ;;
    cw-search) cmd_cw_search "$@" ;;
    cw-delete) cmd_cw_delete "$@" ;;
    loki-search) cmd_loki_search "$@" ;;
    loki-delete) cmd_loki_delete "$@" ;;
    s3-scan) cmd_s3_scan "$@" ;;
    s3-scrub) cmd_s3_scrub "$@" ;;
    s3-restore) cmd_s3_restore "$@" ;;
    s3-delete-before) cmd_s3_delete_before "$@" ;;
    rds-status) cmd_rds_status "$@" ;;
    verify) cmd_verify "$@" ;;
    cleanup) cmd_cleanup "$@" ;;
    help|-h|--help) cmd_help ;;
    *) die "unknown command '$cmd'; run: erasure.sh help" ;;
  esac
}

main "$@"
