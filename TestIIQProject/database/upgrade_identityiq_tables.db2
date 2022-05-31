--
-- This script contains DDL statements to upgrade a database schema to
-- reflect changes to the model.  This file should only be used to
-- upgrade from the last formal release version to the current code base.
--

CONNECT TO iiq;

create table identityiq.spt_bulk_id_join (
   id varchar(32) not null,
    created bigint,
    modified bigint,
    join_id varchar(128),
    join_property varchar(128),
    user_id varchar(128),
    user_id_ci generated always as (upper(user_id)),
    join_property_ci generated always as (upper(join_property)),
    join_id_ci generated always as (upper(join_id)),
    primary key (id)
) IN identityiq_ts;

create index identityiq.spt_bulkidjoin_id_ci on identityiq.spt_bulk_id_join (join_id_ci);

create index identityiq.spt_bulkidjoin_prop_ci on identityiq.spt_bulk_id_join (join_property_ci);

create index identityiq.spt_bulkidjoin_user_ci on identityiq.spt_bulk_id_join (user_id_ci);

-- Add TaskResult.live column
alter table identityiq.spt_task_result add live smallint default 0;
update identityiq.spt_task_result set live = 0;

alter table identityiq.spt_role_change_event add status varchar(255);
alter table identityiq.spt_role_change_event add failed_identity_ids clob(100000000);
alter table identityiq.spt_role_change_event add skipped_identity_ids clob(100000000);
alter table identityiq.spt_role_change_event add affected_identity_count integer default 0 NOT NULL;
alter table identityiq.spt_role_change_event add run_count integer default 0 NOT NULL;
alter table identityiq.spt_role_change_event add failed_attempts integer default 0 NOT NULL;

-- Add iiqDisabled and iiqLocked fields to Link table
alter table identityiq.spt_link add iiq_disabled smallint;
alter table identityiq.spt_link add iiq_locked smallint;
create index identityiq.spt_link_iiq_disabled on identityiq.spt_link (iiq_disabled);
create index identityiq.spt_link_iiq_locked on identityiq.spt_link (iiq_locked);

-- Increase the field size for spt_sign_off_history.account to match spt_link.native_identity
ALTER TABLE identityiq.spt_sign_off_history ALTER COLUMN account SET DATA TYPE varchar(322);

--
-- This is necessary to maintain the schema version. DO NOT REMOVE.
--
update identityiq.spt_database_version set schema_version = '8.2-07' where name = 'main';
