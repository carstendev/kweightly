CREATE TABLE IF NOT EXISTS weight
(
    id                      serial primary key,
    user_id                 varchar(50) NOT NULL,
    recorded_at             timestamp without time zone default current_timestamp,
    weight                  decimal NOT NULL,
    comment                 varchar(255)
);
