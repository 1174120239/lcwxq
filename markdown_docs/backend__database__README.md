# Local Database Assets

`snapshots/lcxqy_2026-07-23_11-17-42.sql` is a MySQL 5.6 export of the `lcxqy` database, copied from the server on 2026-07-23.

The snapshot contains table drops, schema creation, and data. Import it only into a dedicated local development database; never run it against the production server.

Future schema changes for the replacement backend should be added as ordered migration files in this directory, not by editing the snapshot.
