# GDPR erasure runbook

Use this runbook when SNOMED International asks us to erase a person's data from Snap2Snomed under Article 17 of the GDPR (the right to erasure). It covers every place the production system keeps personal data, the order to work in, and the evidence to keep.

The script `erasure.sh` in this folder does the searches, deletions and record keeping. Every command that deletes or rewrites data does a dry run unless you pass `--yes`.

> **Keep personal data out of this repository.** The repository is public. Never commit a request, an email address, a name, a Cognito ID or the evidence folder.

## Who does what

- **SNOMED International** is most likely the controller: it decides why and how the data is used, and it receives the request. Confirm this against the agreement with SNOMED before relying on it.
- **CSIRO** runs the system for SNOMED and carries out the erasure on SNOMED's instructions.
- **One engineer** owns the request from start to finish and keeps the evidence.

The legal deadline is one month from the day SNOMED received the request (GDPR Article 12(3)). SNOMED usually gives us less, so check their message for the date.

## Where personal data lives

| Place | What it holds | Kept for | How to remove it |
|---|---|---|---|
| Cognito user pool `snap2snomed-app` | Email, names, username, `sub`, SNOMED SSO ID | Until deleted | Delete the user |
| Database tables `user` and `user_aud` | Email, names, nickname | Forever | Overwrite with placeholder values (`db-anonymise.sql`) |
| Database free text (notes, descriptions) | Anything a user typed | Forever | Find with `db-find-subject.sql`, edit by hand |
| CloudWatch log groups `*snap2snomed-app*` | Emails in sign-up Lambda, Dex and API logs | Forever | `cw-archive` copies each stream without the person's lines to `<group>-archive`; `cw-delete` then deletes the original |
| Loki (`ontoserver-dev-k8s`, Azure australiaeast) | Copies of the API log group | 365 days | Loki delete request |
| S3 `snap2snomed-backups/cognito/` | One full user-pool export a day | Forever | Rewrite or delete the files |
| RDS automated backups | Whole database | 14 days | Expire, or cut retention |
| RDS manual snapshots | Whole database on the day taken | Forever | Delete, or restore and clean |

Dex also wrote into the API log group, in streams named `snap2snomed-dex/…`, before it had a group of its own. Search both groups.

These facts were checked on 2 October 2026. Check them again if the infrastructure has changed.

## Identifiers to search for

A person can be found by any of these. `erasure.sh init` collects them all from Cognito, or from the newest user-pool export if the account is already gone.

- **Email address,** in any letter case. SNOMED has also seen `mailto:` forms.
- **Cognito `sub`.** It is a UUID, and the database stores it as `user.id` and in every `created_by` and `modified_by` column.
- **Cognito username,** such as `snomed_…` or `snomedinternational_…`. The part after the prefix is the SNOMED SSO user ID in lower case.
- **SNOMED SSO user ID,** from the `identities` attribute.
- **Given name and family name.** Search for these separately, because they give false matches.

## Before you start

1. You need the AWS CLI with the `snap2snomed` profile, `kubectl` with the `ontoserver-dev-k8s` context, and `jq`, `curl` and `shasum`.
2. Use one terminal for the whole request.
3. Choose a request reference with no personal data in it, such as `SI-2026-10-02`.

```sh
export AWS_PROFILE=snap2snomed
cd ops/gdpr-erasure
./erasure.sh help
```

## Steps

### 1. Capture the identifiers

Do this before anyone deletes the Cognito account.

```sh
./erasure.sh init SI-2026-10-02 'person@example.org'
```

If it reports more than one account with the same local part, check each one by hand.

### 2. Delete the Cognito account

Do this early, because the daily export at 04:00 AEST copies every user into S3 (Azure DevOps release definition 42).

```sh
aws cognito-idp admin-delete-user --user-pool-id <pool> --username <username>
```

The pool ID and username are in `~/.gdpr-erasure/<ref>/subject.env`.

### 3. Anonymise the database

Run `db-find-subject.sql`, then `db-anonymise.sql`, against the production database. Review every free-text match by hand. Write down the date of the fix, because the backup steps count from it.

### 4. Search the logs and exports

```sh
./erasure.sh cw-search            # CloudWatch, by ID
./erasure.sh cw-search --names    # CloudWatch, by name; review these by hand
./erasure.sh loki-search
./erasure.sh s3-scan
./erasure.sh rds-status <sign-up date from the database>
```

### 5. Pass the request and legal-hold gate

First, confirm the request is real. A request that arrives in chat, such as Slack, could come from anyone, and an erasure can't be undone. Ask SNOMED to confirm it through a channel you already trust, such as a known email address or a phone call. Ask them to confirm that they have checked the person's identity, which is their job as controller.

Erasure has exceptions (Article 17(3)), for example when the data is needed for a legal claim or a legal obligation. They apply only when there is a real reason now, not a possible one. Ask SNOMED in writing:

> Are you aware of any legal hold, investigation or claim involving this person?

Save both answers with the evidence, then record them:

```sh
./erasure.sh legal-hold cleared "SNOMED email of 2026-10-03: request confirmed, no hold"
# or, if there is a hold:
./erasure.sh legal-hold hold "SNOMED email of 2026-10-03, police request"
```

If there is a hold, stop. Restrict the data (Article 18): keep it, don't use it, and limit who can reach it. Involve CSIRO legal. The script refuses every delete until the gate says `cleared`.

### 6. Delete and rewrite

```sh
./erasure.sh cw-archive           # dry run: lists the streams
./erasure.sh cw-archive --yes     # copy each stream without the person's lines, and check it
./erasure.sh cw-delete            # dry run: checks every archive, deletes nothing
./erasure.sh cw-delete --yes      # deletes the originals, only if every check passes
./erasure.sh loki-delete --yes    # only if loki-search found lines
./erasure.sh s3-scrub --yes
```

CloudWatch can't delete or edit a single log line, so the work is split into two commands.

`cw-archive` copies each matching stream and deletes nothing. For each stream, it:

1. downloads every event
2. leaves out the lines that match the person's IDs, or their given and family name together
3. checks that no match is left
4. writes the rest to a log group named `<group>-archive`, under the same stream name
5. reads the archive back and checks the line count
6. records the archive, with a SHA-256 hash of every source message, in `~/.gdpr-erasure/<ref>/archives.jsonl`

If any step fails, it removes the half-written archive. Running it again skips streams that are already archived and unchanged.

`cw-delete` checks every stream before it deletes any. It stops, deleting nothing, if a stream:

- has no recorded archive
- has changed since it was archived, which it finds by downloading the stream again and comparing the hash
- has an archive that is missing, has a different line count, or still matches the person

Only when every stream passes, the legal-hold gate is cleared and you pass `--yes` does it delete the originals.

If a stream has changed, delete its archive stream and its line in `archives.jsonl`, then run `cw-archive` again. This happens when a stream is still receiving logs.

CloudWatch rejects events older than 14 days, so archive events carry the upload time. Each line's original time is at the start of its message, for example `[2022-05-31T03:52:01.237Z] …`. Search archives with Logs Insights on the message text, not on the time range. The archive groups are made by the script, not by Terraform. They have no retention period and no subscription to Loki. Their names still contain `snap2snomed-app`, so `cw-search` covers them in later requests.

The evidence log records, for each stream, the lines in total, removed and archived, and a SHA-256 hash of the archived messages.

For the user-pool exports in Glacier Flexible Retrieval, choose one:

- **Rewrite them:** run `./erasure.sh s3-restore`, wait 5 to 12 hours, then run `s3-scan` and `s3-scrub --yes` again.
- **Delete them:** agree a retention period with SNOMED, then run `./erasure.sh s3-delete-before <YYYY-MM-DD> --yes`.

For the database backups:

- **Automated backups** expire 14 days after the database fix. If anyone restores one before then, run `db-anonymise.sql` again before the copy goes live. The other choice is to set retention to 1 day and back to 14, which deletes them at once but loses recovery points.
- **Manual snapshots** that `rds-status` marks `MAY HOLD THE PERSON` need deleting, or restoring, cleaning and taking again.

### 7. Verify

```sh
./erasure.sh verify
```

All searches should come back empty. A Loki delete request takes effect about 25 hours after it is filed.

### 8. Close the request

1. Add a line to the erasure register (see below).
2. Reply to SNOMED: what you removed, where, and when the remaining backups expire.
3. Copy the evidence folder to the records location.
4. Delete the local identifiers: `./erasure.sh cleanup --yes`.

## Evidence

The aim is to show what you did, when, and on whose instruction, without keeping the personal data you erased.

`erasure.sh` writes every search, decision and deletion to `~/gdpr-evidence/<ref>/evidence.jsonl`. Each line has a time, the AWS identity that ran it, and a subject reference: the first 16 characters of the SHA-256 hash of the person's `sub`. It also saves the names of the log streams it found. It never writes an email, name or ID.

Keep these together in one access-controlled folder in CSIRO's records system, not in git or chat:

- SNOMED's request, as received, and their confirmation through a trusted channel.
- The legal-hold question and SNOMED's answer.
- The `~/gdpr-evidence/<ref>/` folder.
- The database team's confirmation, with the date of the fix.
- Your reply to SNOMED.

### Erasure register

Keep one register for all requests, in the same restricted location. Each row holds:

- the request reference
- the date received
- the date closed
- the person's `sub`
- what was removed
- when the last backup expires

Anyone who restores a database backup or a user-pool export must apply every erasure in the register to the restored copy before using it. This is what the EDPB expects when backups are not changed straight away.

## What the guidance says

From the EDPB's report on its 2025 coordinated action on the right to erasure, adopted 18 February 2026:

- **Backups (Issue 6):** You may leave a backup unchanged if changing it is risky. In that case, you must record the erasure and apply it again to any restored system. Waiting for backups to be overwritten, with no procedure, was criticised.
- **Proof (Issue 6):** Erase in a structured way, check that it worked, and be able to show it. Overwriting personal data with random strings is named as good practice.
- **Anonymisation (Issue 7):** Partial masking or reversible pseudonymisation is not erasure.
- **Exceptions (Issue 4):** Assess each case, put any refusal in writing, and involve legal or compliance staff.

Sources:

- [EDPB CEF 2025 report on the right to erasure](https://www.edpb.europa.eu/system/files/2026-02/edpb_cef-report_2025_right-to-erasure_en.pdf)
- [GDPR Article 17](https://gdpr-info.eu/art-17-gdpr/)

## Known gaps

These make each request harder than it needs to be:

- The API, Dex and sign-up Lambda log groups have no retention period.
- The `snap2snomed-backups` bucket has no expiry rule.
- The sign-up Lambda logs email addresses (`terraform/cognito/pre_signup_lambda/pre_signup_lambda.mjs`).
- Loki stores EU users' log data in an Australian storage account.
