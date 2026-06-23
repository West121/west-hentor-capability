#!/usr/bin/env node

throw new Error(
  'import-production-sql.mjs is disabled. The replica reads and writes SQL Server directly; use scripts/import-production-sql-to-mssql.sh instead.',
);
