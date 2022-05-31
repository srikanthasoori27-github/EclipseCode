-- You can define here all the statements that should be excluded by the schema checker,
-- these statements are not going to be included in the upgrade file.
alter table identityiq.spt_sign_off_history modify account varchar(322);