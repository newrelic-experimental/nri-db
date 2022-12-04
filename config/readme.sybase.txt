Create a new login user with "mon_role":

use master
sp_addlogin newrelic, "NewRelic1!", master
sp_role "grant", mon_role, newrelic

Enable monitoring:
sp_configure "enable monitoring", 1
sp_configure "enable stmt cache monitoring", 1
sp_configure "execution time monitoring", 1
sp_configure "statement cache size", 100