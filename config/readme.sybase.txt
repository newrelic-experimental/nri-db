If you don't already have a suitable user login, create a new one with a monitoring role such as "mon_role".

use master
sp_addlogin newrelic, "NewRelic1!", master
sp_role "grant", mon_role, newrelic
