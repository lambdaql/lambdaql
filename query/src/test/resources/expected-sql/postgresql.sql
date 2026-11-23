-- SQL that Hibernate 7.4 makes for the manual examples on postgresql.
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
where (o1_0.status=1 or o1_0.status=3) and o1_0.active=true

-- @example string-literal
-- HQL: select e0 from Order e0 where e0.description = 'O''Brien'
select …
from orders o1_0
where o1_0.description='O''Brien'

-- @example null-safe
-- HQL: select e0 from Order e0 where e0.description = :p1
select …
from orders o1_0
where o1_0.description=?

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
-- HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name = :p1
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
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
where o1_0.active=true

-- @example boolean#2
-- HQL: select e0 from Order e0 where e0.active = false
select …
from orders o1_0
where o1_0.active=false

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
-- HQL: select e0 from Order e0 where e0.description not in (:p1)
select …
from orders o1_0
where o1_0.description not in (?,?)

-- @example overview-first
-- HQL: select e0 from Order e0 join e0.customer j1 where (e0.status = 1 or j1.name = 'Kim') and e0.price > :p1
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
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
where o1_0.active=true and (o1_0.status=1 or o1_0.status=2)

-- @example many-where
-- HQL: select e0 from Order e0 where e0.status = 1 and e0.active = true
select …
from orders o1_0
where o1_0.status=1 and o1_0.active=true

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
-- HQL: select e0 from Order e0 join e0.customer j1 where e0.customer is null or j1.name is null
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.id is null or c1_0.name is null

-- @example sort-basic
-- HQL: select e0 from Order e0 where e0.active = true order by e0.price desc, e0.id asc
select …
from orders o1_0
where o1_0.active=true
order by o1_0.price desc,o1_0.id

-- @example sort-nulls
-- HQL: select e0 from Order e0 order by e0.description asc nulls first, e0.id asc
select …
from orders o1_0
order by o1_0.description asc nulls first,o1_0.id

-- @example sort-nulls#2
-- HQL: select e0 from Order e0 order by e0.description asc nulls last, e0.id asc
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- @example sort-nulls#3
-- HQL: select e0 from Order e0 order by e0.description desc nulls first, e0.id asc
select …
from orders o1_0
order by o1_0.description desc,o1_0.id

-- @example sort-dynamic
-- HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id asc
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id

-- @example sort-dynamic#2
-- HQL: select e0 from Order e0 order by e0.description asc nulls last, e0.id asc
select …
from orders o1_0
order by o1_0.description,o1_0.id

-- @example sort-path
-- HQL: select e0 from Order e0 left join e0.customer j1 order by j1.name asc nulls first, e0.id asc
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
order by c1_0.name asc nulls first,o1_0.id

-- @example sort-path-where
-- HQL: select e0 from Order e0 join e0.customer j1 where j1.name = :p1 order by j1.name desc nulls last, e0.price desc
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name=?
order by c1_0.name desc nulls last,o1_0.price desc

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
offset ? rows
fetch first ? rows only

-- @example page-only
-- HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- @example sort-spec
-- HQL: select e0 from Order e0 where e0.active = true order by e0.description desc nulls last, e0.id asc
select …
from orders o1_0
where o1_0.active=true
order by o1_0.description desc nulls last,o1_0.id

-- @example sort-spec-then
-- HQL: select e0 from Order e0 order by e0.status asc, e0.price desc
select …
from orders o1_0
order by o1_0.status,o1_0.price desc

-- @example sort-spec-reversed
-- HQL: select e0 from Order e0 order by e0.description desc nulls last, e0.id desc
select …
from orders o1_0
order by o1_0.description desc nulls last,o1_0.id desc

-- @example first
-- HQL: select e0 from Order e0 order by e0.price asc, e0.id asc
select …
from orders o1_0
order by o1_0.price,o1_0.id
fetch first ? rows only

-- @example first#2
-- HQL: select e0 from Order e0 where e0.active = true order by e0.id asc
select …
from orders o1_0
where o1_0.active=true
order by o1_0.id
fetch first ? rows only

-- @example one
-- HQL: select e0 from Order e0 where e0.id = :p1
select …
from orders o1_0
where o1_0.id=?
fetch first ? rows only

-- @example count
-- HQL: select count(e0) from Order e0 where e0.active = true
select count(o1_0.id)
from orders o1_0
where o1_0.active=true

-- @example exists
-- HQL: select 1 from Order e0 where e0.kind = org.lambdaql.query.Order$Kind.RUSH
select 1
from orders o1_0
where o1_0.kind='RUSH'
fetch first ? rows only

-- @example readme-first
-- HQL: select e0 from Order e0 where e0.status = 1 and e0.price > :p1 order by e0.price desc nulls last, e0.id asc
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1 and o1_0.price>?
order by o1_0.price desc nulls last,o1_0.id
fetch first ? rows only

-- @example sort-overview
-- HQL: select e0 from Order e0 left join e0.customer j1 where e0.active = true order by e0.price desc nulls last, j1.name asc nulls first, e0.id asc
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.active=true
order by o1_0.price desc nulls last,c1_0.name asc nulls first,o1_0.id
offset ? rows
fetch first ? rows only

-- @example page-design
-- HQL: select e0 from Order e0 where e0.status = 1 order by e0.price desc nulls last, e0.id asc
-- (this HQL is given to Hibernate directly)
select …
from orders o1_0
where o1_0.status=1
order by o1_0.price desc nulls last,o1_0.id
offset ? rows
fetch first ? rows only

-- @example update
-- HQL: update Order e0 set e0.status = :p1, e0.price = (e0.price * 2.0) where e0.status = 1
update orders o1_0
set status=?,price=(o1_0.price*2.0)
where o1_0.status=1

-- @example update-value
-- HQL: update Order e0 set e0.description = 'done' where e0.status = 2
update orders o1_0
set description='done'
where o1_0.status=2

-- @example update-path
-- HQL: update Order e0 set e0.custName = 'K' where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim' and e0.customer.id is not null
update orders o1_0
set cust_name='K'
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim' and o1_0.customer_id is not null

-- @example update-path#2
-- HQL: update Order e0 set e0.custName = (select s1.name from Customer s1 where s1.id = fk(e0.customer))
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- @example update-versioned
-- HQL: update versioned VOrder e0 set e0.status = 2 where e0.id = 1L
update v_orders v1_0
set status=2,version=(v1_0.version+1)
where v1_0.id=1

-- @example delete
-- HQL: delete from Order e0 where e0.status = 3
delete from orders o1_0
where o1_0.status=3

-- @example delete#2
-- HQL: delete from Order e0 where (select s1.name from Customer s1 where s1.id = fk(e0.customer)) is null and e0.customer.id is not null
delete from orders o1_0
where (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id) is null and o1_0.customer_id is not null

-- @example insert
-- HQL: insert into Archive (id, price, customerName) values (100L, 9.5, 'Kim')
insert into archive as a1_0(id,price,customer_name)
values (100,9.5,'Kim')

-- @example insert-list
-- HQL: insert into Archive (id, price) values (:p1, :p2), (:p3, :p4)
insert into archive as a1_0(id,price)
values (?,?), (?,?)

-- @example insert-select
-- HQL: insert into Archive (id, price, customerName) select e0.id, e0.price, j1.name from Order e0 left join e0.customer j1 where e0.status = 1
insert into archive as a1_0(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1

-- @example upsert
-- HQL: insert into Archive e0 (id, price) values (100L, 2.0) on conflict(id) do update set price = excluded.price
insert into archive as a1_0(id,price)
values (100,2.0) on conflict(id) do update
set price=excluded.price

-- @example on-conflict
-- HQL: insert into Archive e0 (id, price) values (100L, 5.0) on conflict(id) do update set price = (e0.price + 5.0)
insert into archive as a1_0(id,price)
values (100,5.0) on conflict(id) do update
set price=(a1_0.price+5.0)

-- @example insert-ignore
-- HQL: insert into Archive (id, price) select 300L, 1.0 where not exists (select 1 from Archive x where x.id = 300L)
insert into archive as a1_0(id,price)
select 300,1.0
where not exists(select 1 from archive a2_0 where a2_0.id=300)

-- @example safe-call-or
-- HQL: select e0 from Order e0 left join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1 or c1_0.name='Kim'

-- @example safe-call-null
-- HQL: select e0 from Order e0 left join e0.customer j1 where j1.name is null
select …
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name is null

-- @example safe-call-and
-- HQL: select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' and e0.price > 100.0
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim' and o1_0.price>100.0

-- @example not-null-assert
-- HQL: select e0 from Order e0 join e0.customer j1 where e0.status = 1 or j1.name = 'Kim'
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=1 or c1_0.name='Kim'

-- @example mixed-warn
-- HQL: select e0 from Order e0 join e0.customer j1 where j1.name = 'Kim' or j1.grade > 3
select …
from orders o1_0
join customers c1_0 on c1_0.id=o1_0.customer_id
where c1_0.name='Kim' or c1_0.grade>3

-- @example update-safe-call
-- HQL: update Order e0 set e0.status = :p1 where e0.status = 1 or (select s1.name from Customer s1 where s1.id = fk(e0.customer)) = 'Kim'
update orders o1_0
set status=?
where o1_0.status=1 or (select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)='Kim'

-- @example sub-exists
-- HQL: select e0 from Order e0 where exists (select 1 from Item s1 where s1.order.id = e0.id and s1.qty > 10)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)

-- @example sub-not-exists
-- HQL: select e0 from Order e0 where e0.status = 1 or not exists (select 1 from Item s1 where s1.order.id = e0.id)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- @example sub-in
-- HQL: select e0 from Order e0 where e0.customer.id in (select s1.id from Customer s1 where s1.grade is not null and s1.grade > 3)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade is not null and c2_0.grade>3)

-- @example sub-not-in
-- HQL: select e0 from Order e0 where e0.status not in (select s1.grade from Customer s1)
select …
from orders o1_0
where o1_0.status not in (select c2_0.grade from customers c2_0)

-- @example sub-scalar
-- HQL: select e0 from Order e0 where e0.price > (select avg(s1.price) from Order s1)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)

-- @example sub-count
-- HQL: select e0 from Order e0 where (select count(s1) from Item s1 where s1.order.id = e0.id) >= 2L
select …
from orders o1_0
where (select count(i1_0.id) from items i1_0 where i1_0.order_id=o1_0.id)>=2

-- @example sub-top-one
-- HQL: select e0 from Order e0 where e0.id = (select s1.id from Order s1 where s1.customer.id = e0.customer.id order by s1.price desc, s1.id asc limit 1)
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id=o1_0.customer_id order by o2_0.price desc,o2_0.id fetch first 1 rows only)

-- @example sub-nested
-- HQL: select e0 from Customer e0 where exists (select 1 from Order s1 where s1.customer.id = e0.id and exists (select 1 from Item s2 where s2.order.id = s1.id and s2.qty > 10))
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))

-- @example value
-- HQL: select e0.price from Order e0 where e0.status = 1 order by e0.price asc
select o1_0.price
from orders o1_0
where o1_0.status=1
order by o1_0.price

-- @example record
-- HQL: select e0.id, j1.name, e0.price from Order e0 left join e0.customer j1
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id

-- @example group-by
-- HQL: select e0.customer.id, count(*), sum(e0.price) from Order e0 group by e0.customer.id having sum(e0.price) > 200.0
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>200.0

-- @example group-many
-- HQL: select e0.customer.id, e0.status, count(*) from Order e0 where e0.customer is not null group by e0.customer.id, e0.status
select o1_0.customer_id,o1_0.status,count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id,o1_0.status

-- @example total
-- HQL: select count(*), avg(e0.price) from Order e0 where e0.status = 1
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1

-- @example distinct
-- HQL: select distinct e0.price from Order e0 order by e0.price desc
select distinct o1_0.price
from orders o1_0
order by o1_0.price desc

-- @example value-page
-- HQL: select e0.price from Order e0 order by e0.price desc, e0.id asc
select o1_0.price
from orders o1_0
order by o1_0.price desc,o1_0.id
offset ? rows
fetch first ? rows only

-- @example insert-group
-- HQL: insert into Summary (customerId, total, cnt) select e0.customer.id, sum(e0.price), count(*) from Order e0 where e0.customer is not null group by e0.customer.id having count(*) >= 2L
insert into summary as s1_0(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
where o1_0.customer_id is not null
group by o1_0.customer_id
having count(*)>=2

-- @example delete-limit
-- HQL: delete from Order e0 where e0.id in (select t.id from (select x0.id as id from Order x0 where x0.status = 1 order by x0.price asc, x0.id asc limit 1) t)
delete from orders o1_0
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 where o2_0.status=1 order by o2_0.price,o2_0.id fetch first 1 rows only) t1_0(c0))

-- @example dml-subquery
-- HQL: delete from Order e0 where not exists (select 1 from Item s1 where s1.order.id = e0.id)
delete from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- @example update-limit
-- HQL: update Order e0 set e0.description = 'top' where e0.id in (select t.id from (select x0.id as id from Order x0 join x0.customer k1 where k1.name = 'Kim' order by x0.price desc, x0.id asc limit 1) t)
update orders o1_0
set description='top'
where o1_0.id in (select t1_0.c0 from (select o2_0.id from orders o2_0 join customers c1_0 on c1_0.id=o2_0.customer_id where c1_0.name='Kim' order by o2_0.price desc,o2_0.id fetch first 1 rows only) t1_0(c0))
