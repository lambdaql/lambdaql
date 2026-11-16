-- SQL that Hibernate 6.6 makes for the manual examples on mariadb.
-- Made by SqlByDatabaseTest; the select list is shortened to … and the test table prefix is removed.

-- @example to-hql
-- HQL: select e0 from Order e0 where e0.status = 1 and e0.price > 100.0
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>100.0

-- @example to-hql#2
-- HQL: select e0 from Order e0 where e0.status = 1 and e0.price > :p1
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?

-- @example where-many
-- HQL: select e0 from Order e0 where (e0.status = 1 or e0.status = 3) and e0.active = true
select …
from orders o1_0
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=1

-- @example string-literal
-- HQL: select e0 from Order e0 where e0.description = 'O''Brien'
select …
from orders o1_0
where o1_0.description='O''Brien'

-- @example null-safe
-- HQL: select e0 from Order e0 where e0.description is null
select …
from orders o1_0
where o1_0.description is null

-- @example null-safe#2
-- HQL: select e0 from Order e0 where e0.description = :p1
select …
from orders o1_0
where o1_0.description=?

-- @example inner-join
-- HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?

-- @example null-guard
-- HQL: select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name = :p1
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name=?

-- @example null-guard#2
-- HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is not null and j1.name = :p1
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is not null and c1_0.name=?

-- @example captured-null-guard
-- HQL: select e0 from Order e0
select …
from orders o1_0

-- @example captured-null-guard#2
-- HQL: select e0 from Order e0 where e0.price > :p1
select …
from orders o1_0
where o1_0.price>?

-- @example fk-path
-- HQL: select e0 from Order e0 where e0.customer.id = 1
select …
from orders o1_0
where o1_0.customer_id=1

-- @example no-association
-- HQL: select e0 from Order e0 where e0.customer is null
select …
from orders o1_0
where o1_0.customer_id is null

-- @example in
-- HQL: select e0 from Order e0 where e0.status in (:p1)
select …
from orders o1_0
where o1_0.status in (?,?)

-- @example boolean
-- HQL: select e0 from Order e0 where e0.active = true
select …
from orders o1_0
where o1_0.active=1

-- @example boolean#2
-- HQL: select e0 from Order e0 where e0.active = false
select …
from orders o1_0
where o1_0.active=0

-- @example enum
-- HQL: select e0 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH
select …
from orders o1_0
where o1_0.kind='RUSH'

-- @example not-in
-- HQL: select e0 from Order e0 where e0.id not in (:p1)
select …
from orders o1_0
where o1_0.id not in (?,?)

-- @example not-in#2
-- HQL: select e0 from Order e0 where (e0.description is null or e0.description not in (:p1))
select …
from orders o1_0
where (o1_0.description is null or o1_0.description not in (?,?))

-- @example overview-first
-- HQL: select e0 from Order e0 left join e0.customer j1 where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where (o1_0.status=1 or c1_0.name='Kim') and o1_0.price>?

-- @example start-captured
-- HQL: select e0 from Order e0 where e0.status = :p1 and e0.price >= :p2
select …
from orders o1_0
where o1_0.status=? and o1_0.price>=?

-- @example start-many-where
-- HQL: select e0 from Order e0 where e0.active = true and (e0.status = 1 or e0.status = 2)
select …
from orders o1_0
where o1_0.active=1 and (o1_0.status=1 or o1_0.status=2)

-- @example many-where
-- HQL: select e0 from Order e0 where e0.status = 1 and e0.active = true
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=1

-- @example this-capture
-- HQL: select e0 from Order e0 where e0.status = :p1
select …
from orders o1_0
where o1_0.status=?

-- @example path-literal
-- HQL: select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim'
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim'

-- @example fk-literal
-- HQL: select e0 from Order e0 where e0.customer.id = 5
select …
from orders o1_0
where o1_0.customer_id=5

-- @example null-guard-name-null
-- HQL: select e0 from Order e0 left join e0.customer j1 where e0.customer is null or j1.name is null
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name is null

-- @example sort-basic
-- HQL: select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc
select …
from orders o1_0
where o1_0.active=1
order by o1_0.price desc,o1_0.id

-- @example sort-nulls
-- HQL: select e0 from Order e0 order by e0.description asc nulls first, e0.id asc
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- @example sort-nulls#2
-- HQL: select e0 from Order e0 order by e0.description asc nulls last, e0.id asc
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- @example sort-nulls#3
-- HQL: select e0 from Order e0 order by e0.description desc nulls first, e0.id asc
select …
from orders o1_0
order by case when (o1_0.description) is null then 0 else 1 end,o1_0.description desc,o1_0.id

-- @example sort-dynamic
-- HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id asc
select …
from orders o1_0
order by o1_0.description desc,o1_0.id

-- @example sort-dynamic#2
-- HQL: select e0 from Order e0 order by e0.description asc nulls last, e0.id asc
select …
from orders o1_0
order by case when (o1_0.description) is null then 1 else 0 end,o1_0.description,o1_0.id

-- @example sort-path
-- HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name,o1_0.id

-- @example sort-path-where
-- HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1 order by j1.name desc nulls last, e0.price desc
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc,o1_0.price desc

-- @example sort-key
-- HQL: select e0 from Order e0 order by e0.price asc
select …
from orders o1_0
order by o1_0.price

-- @example sort-key#2
-- HQL: select e0 from Order e0 order by e0.price desc
select …
from orders o1_0
order by o1_0.price desc

-- @example sort-page
-- HQL: select e0 from Order e0 order by e0.status asc, e0.id asc
select …
from orders o1_0
order by o1_0.status,o1_0.id
limit ?,?

-- @example page-only
-- HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- @example sort-spec
-- HQL: select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc
select …
from orders o1_0
where o1_0.active=1
order by o1_0.description desc,o1_0.id

-- @example sort-spec-then
-- HQL: select e0 from Order e0 order by e0.status asc, e0.price desc
select …
from orders o1_0
order by o1_0.status,o1_0.price desc

-- @example sort-spec-reversed
-- HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id desc
select …
from orders o1_0
order by o1_0.description desc,o1_0.id desc

-- @example first
-- HQL: select e0 from Order e0 order by e0.price asc, e0.id asc
select …
from orders o1_0
order by o1_0.price,o1_0.id
limit ?

-- @example first#2
-- HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
select …
from orders o1_0
where o1_0.active=1
order by o1_0.id
limit ?

-- @example one
-- HQL: select e0 from Order e0 where e0.id = :p1
select …
from orders o1_0
where o1_0.id=?
limit ?

-- @example count
-- HQL: select count(e0) from Order e0 where e0.active = true
select count(o1_0.id)
from orders o1_0
where o1_0.active=1

-- @example exists
-- HQL: select 1 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH
select 1
from orders o1_0
where o1_0.kind='RUSH'
limit ?

-- @example readme-first
-- HQL: select e0 from Order e0 where e0.status = 1 and e0.price > :p1 order by e0.price desc nulls last, e0.id asc
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc,o1_0.id
limit ?

-- @example sort-overview
-- HQL: select e0 from Order e0 left join e0.customer j1 where e0.active = true order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=1
order by o1_0.price desc,c1_0.name,o1_0.id
limit ?,?

-- @example page-design
-- HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc,o1_0.id
limit ?,?

-- @example update
-- HQL: update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1
update orders o1_0
set status=?,price=(o1_0.price*2.0)
where o1_0.status=1

-- @example update-by
-- HQL: update Order e0 set e0.description = 'done' where e0.status = 2
update orders o1_0
set description='done'
where o1_0.status=2

-- @example update-path
-- HQL: update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = e0.customer.id) = 'Kim' and e0.customer.id is not null
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- @example update-path#2
-- HQL: update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = e0.customer.id)
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- @example update-versioned
-- HQL: update versioned VOrder e0 set e0.status = 2 where e0.id = 1L
update v_orders v1_0
set status=2,version=(v1_0.version+1)
where v1_0.id=1

-- @example delete
-- HQL: delete from Order e0 where e0.status = 3
delete o1_0 from orders o1_0
where o1_0.status=3

-- @example delete#2
-- HQL: delete from Order e0 where (select s1.name from Customer s1 where s1.id = e0.customer.id) is null and e0.customer.id is not null
delete o1_0 from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- @example insert
-- HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
insert into archive(id,price,customer_name)
values (100,9.5,'Kim')

-- @example insert-list
-- HQL: insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)
insert into archive(id,price)
values (?,?), (?,?)

-- @example insert-select
-- HQL: insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name from Order e0 left join e0.customer j1 where e0.status = 1
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1

-- @example upsert
-- HQL: insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price
insert into archive(id,price)
values (100,2.0) on duplicate key update price=values(price)

-- @example on-conflict
-- HQL: insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)
insert into archive(id,price)
values (100,5.0) on duplicate key update price=(archive.price+5.0)

-- @example insert-ignore
-- HQL: insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)
insert into archive(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)

-- @example delete-limit
-- HQL: delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0 where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)
delete o1_0 from orders o1_0
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id limit 1) t1_0)

-- @example update-limit
-- HQL: update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0 join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.id from (select o2_0.id id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id limit 1) t1_0)
