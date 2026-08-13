ALTER TABLE posting_participation
    ADD COLUMN participation_start_date DATE NULL AFTER status,
    ADD COLUMN participation_end_date   DATE NULL AFTER participation_start_date;