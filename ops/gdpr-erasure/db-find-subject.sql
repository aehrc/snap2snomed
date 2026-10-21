-- Find one person in the Snap2Snomed database. Read only.
-- Fill in the five values from ~/.gdpr-erasure/<ref>/subject.env, then run the whole file.
-- Never commit this file with real values in it.

SET @sub      = '<SUB>';
SET @email    = '<EMAIL>';
SET @username = '<SUBJECT_USERNAME>';
SET @given    = '<GIVEN>';
SET @family   = '<FAMILY>';

-- 1. The account row and its audit history.
SELECT 'user' AS tbl, id, created, modified, accepted_terms_version FROM `user` WHERE id = @sub;
SELECT 'user_aud' AS tbl, COUNT(*) AS versions FROM user_aud WHERE id = @sub;

-- 2. Every text column in the schema that mentions the email, username or family name.
--    Family-name matches can be other people; review them by hand.
SET SESSION group_concat_max_len = 4194304;
SELECT GROUP_CONCAT(
         CONCAT('SELECT ''', table_name, ''' AS tbl, ''', column_name, ''' AS col, COUNT(*) AS hits FROM `',
                table_name, '` WHERE LOWER(`', column_name, '`) LIKE LOWER(CONCAT(''%'', @email, ''%''))',
                ' OR LOWER(`', column_name, '`) LIKE LOWER(CONCAT(''%'', @username, ''%''))',
                ' OR LOWER(`', column_name, '`) LIKE LOWER(CONCAT(''%'', @family, ''%''))')
         SEPARATOR ' UNION ALL ')
  INTO @q
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND data_type IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext')
   AND NOT (table_name IN ('user', 'user_aud') AND column_name IN ('email', 'given_name', 'family_name', 'nickname'));
SET @q = CONCAT('SELECT * FROM (', @q, ') t WHERE hits > 0 ORDER BY tbl, col');
PREPARE find_subject FROM @q;
EXECUTE find_subject;
DEALLOCATE PREPARE find_subject;

-- 3. For each table and column found above, look at the rows, for example:
-- SELECT id, note_text FROM note WHERE LOWER(note_text) LIKE LOWER(CONCAT('%', @email, '%'));
