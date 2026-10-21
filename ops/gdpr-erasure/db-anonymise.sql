-- Anonymise one person in the Snap2Snomed database.
-- Run db-find-subject.sql first. Fill in the values, run the whole file, check the counts, then COMMIT.
-- The row stays, because notes, tasks and map rows point at user.id. Its personal fields get
-- random values that link to nobody. user.id (the Cognito sub) also stays; it identifies no one once
-- the Cognito account and every user-pool export holding it are gone.
-- Never commit this file with real values in it.

SET @sub    = '<SUB>';
SET @email  = '<EMAIL>';
SET @given  = '<GIVEN>';
SET @family = '<FAMILY>';
SET @anon   = CONCAT('erased-', LEFT(SHA2(UUID(), 256), 12));
SET @email_re = CONCAT('(?i)\\Q', @email, '\\E');   -- the email as a literal, any letter case

START TRANSACTION;

UPDATE `user`
   SET email = CONCAT(@anon, '@erased.invalid'), given_name = 'Erased', family_name = 'User', nickname = @anon
 WHERE id = @sub;
SELECT ROW_COUNT() AS user_rows;          -- expect 1

UPDATE user_aud
   SET email = CONCAT(@anon, '@erased.invalid'), given_name = 'Erased', family_name = 'User', nickname = @anon
 WHERE id = @sub;
SELECT ROW_COUNT() AS user_aud_rows;      -- expect the number of versions db-find-subject.sql reported

-- Free text: replace the email wherever db-find-subject.sql found it. Names need a human decision,
-- because a family name can belong to someone else. Add a statement for every other table and column it reported.
UPDATE note     SET note_text = REGEXP_REPLACE(note_text, @email_re, '[removed]') WHERE note_text REGEXP @email_re;
SELECT ROW_COUNT() AS note_rows;
UPDATE note_aud SET note_text = REGEXP_REPLACE(note_text, @email_re, '[removed]') WHERE note_text REGEXP @email_re;
SELECT ROW_COUNT() AS note_aud_rows;

-- Check: all three must return 0.
SELECT COUNT(*) AS user_rows_still_named FROM `user`   WHERE id = @sub AND (email = @email OR family_name = @family);
SELECT COUNT(*) AS aud_rows_still_named  FROM user_aud WHERE id = @sub AND (email = @email OR family_name = @family);
SELECT COUNT(*) AS notes_with_email      FROM (SELECT note_text FROM note UNION ALL SELECT note_text FROM note_aud) n WHERE note_text REGEXP @email_re;

-- If every count is as expected:
-- COMMIT;
-- Otherwise:
-- ROLLBACK;
