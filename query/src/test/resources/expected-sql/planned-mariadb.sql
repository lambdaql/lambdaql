-- SQL that Hibernate 6.6 makes on mariadb for the PLANNED HQL in the manual (lambdaQL does not make these queries yet).
-- Made by PlannedSqlTest: the HQL is given to Hibernate directly. The select list of entity queries
-- is shortened to …, and the test prefix SQ_ is removed.
-- "NOT VALID HQL": Hibernate rejected the HQL. "DB ERROR": Hibernate made the SQL, but the database rejected it.

-- @example 05-exists
-- HQL: select o from Order o where exists (select 1 from Item i where i.order.id = o.id and i.qty > 10)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10)

-- @example 05-not-exists-or
-- HQL: select o from Order o where o.status = 1 or not exists (select 1 from Item i where i.order.id = o.id)
select …
from orders o1_0
where o1_0.status=1 or not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- @example 05-in-subquery
-- HQL: select o from Order o where o.customer.id in (select c.id from Customer c where c.grade > 3)
select …
from orders o1_0
where o1_0.customer_id in (select c2_0.id from customers c2_0 where c2_0.grade>3)

-- @example 05-not-in
-- HQL: select o from Order o where not exists (select 1 from Customer c where c.grade > 3 and c.id = o.customer.id)
select …
from orders o1_0
where not exists(select 1 from customers c2_0 where c2_0.grade>3 and c2_0.id=o1_0.customer_id)

-- @example 05-correlated-param
-- HQL: select o from Order o where exists (select 1 from Item i where i.order.id = o.id and i.qty > :p1)
select …
from orders o1_0
where exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>?)

-- @example 05-scalar-avg
-- HQL: select o from Order o where o.price > (select avg(x.price) from Order x)
select …
from orders o1_0
where o1_0.price>(select avg(o2_0.price) from orders o2_0)

-- @example 05-nested
-- HQL: select c from Customer c where exists (select 1 from Order o where o.customer.id = c.id and exists (select 1 from Item i where i.order.id = o.id and i.qty > 10))
select …
from customers c1_0
where exists(select 1 from orders o1_0 where o1_0.customer_id=c1_0.id and exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id and i1_0.qty>10))

-- @example 06-select-value
-- HQL: select o.price from Order o where o.status = 1
select o1_0.price
from orders o1_0
where o1_0.status=1

-- @example 06-select-path
-- HQL: select c1.name from Order o left join o.customer c1
select c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id

-- @example 06-dto
-- HQL: select o.id, c1.name, o.price from Order o left join o.customer c1
select o1_0.id,c1_0.name,o1_0.price
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id

-- @example 06-aggregate
-- HQL: select count(*), avg(o.price) from Order o where o.status = 1
select count(*),avg(o1_0.price)
from orders o1_0
where o1_0.status=1

-- @example 06-group-by
-- HQL: select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id

-- @example 06-group-having
-- HQL: select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id having sum(o.price) > 1000
select o1_0.customer_id,count(*),sum(o1_0.price)
from orders o1_0
group by o1_0.customer_id
having sum(o1_0.price)>1000

-- @example 06-derived
-- HQL: select r.customerId, r.cnt, r.total from (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) r where r.total > 100
select r1_0.customerId,r1_0.cnt,r1_0.total
from (select o1_0.customer_id customerId,count(*) cnt,sum(o1_0.price) total from orders o1_0 group by o1_0.customer_id) r1_0
where r1_0.total>100

-- @example 06-cte
-- HQL: with t as (select o.customer.id as customerId, count(*) as cnt, sum(o.price) as total from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100
with t (customerId,cnt,total) as (select o1_0.customer_id,count(*),sum(o1_0.price) from orders o1_0 group by o1_0.customer_id)
select x1_0.customerId,x1_0.cnt,x1_0.total
from t x1_0
where x1_0.total>100

-- @example 07-update
-- HQL: update Order o set o.status = :p1, o.price = o.price * 1.1 where o.status = 1
update orders o1_0
set status=?,price=(o1_0.price*1.1)
where o1_0.status=1

-- @example 07-update-coalesce
-- HQL: update Order o set o.price = coalesce(o.price, 0.0) + 1
update orders o1_0
set price=(coalesce(o1_0.price,0.0)+1)

-- @example 07-update-embedded
-- HQL: update Order o set o.address.city = :p1 where o.id = :p2
update orders o1_0
set city=?
where o1_0.id=?

-- @example 07-update-fk
-- HQL: update Order o set o.customer.id = :p1 where o.id = :p2
update orders o1_0
set customer_id=?
where o1_0.id=?

-- @example 07-update-fk-null
-- HQL: update Order o set o.customer = null where o.id = :p1
update orders o1_0
set customer_id=null
where o1_0.id=?

-- @example 07-update-other-table
-- HQL: update Order o set o.custName = (select c.name from Customer c where c.id = o.customer.id)
update orders o1_0
set cust_name=(select c1_0.name from customers c1_0 where c1_0.id=o1_0.customer_id)

-- @example 07-delete-exists
-- HQL: delete from Order o where exists (select 1 from Customer c where c.id = o.customer.id and c.name is null)
delete o1_0 from orders o1_0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.name is null)

-- @example 07-insert-select
-- HQL: insert into Archive (id, price, customerName) select o.id, o.price, c.name from Order o left join o.customer c where o.status = 9
insert into archive(id,price,customer_name)
select o1_0.id,o1_0.price,c1_0.name
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id
where o1_0.status=9

-- @example 08-function
-- HQL: select o from Order o where function('FN_DISCOUNT' as Double, o.price) > 10
select …
from orders o1_0
where fn_discount(o1_0.price)>10

-- @example 08-function-alone
-- HQL: select function('FN_DISCOUNT' as Double, :p1)
select fn_discount(?)

-- ---- The examples below have no HQL in the manual (it shows only the Java code).
-- ---- PlannedSqlTest wrote their HQL from that code and the rules of the manual.

-- @example 05-scalar-top1
-- HQL: select o from Order o where o.id = (select x.id from Order x where x.customer.id is not distinct from o.customer.id order by x.updateAt desc nulls last, x.id asc limit 1)
select …
from orders o1_0
where o1_0.id=(select o2_0.id from orders o2_0 where o2_0.customer_id<=>o1_0.customer_id order by o2_0.update_at desc,o2_0.id limit 1)

-- @example 06-cte-columns
-- HQL: with t (customerId, cnt, total) as (select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100
-- NOT VALID HQL: SyntaxException: At 1:7 and token '(', no viable alternative at input 'with t *(customerId, cnt, total) as (select o.customer.id, count(*), sum(o.price) from Order o group by o.customer.id) select x.customerId, x.cnt, x.total from t x where x.total > 100'

-- @example 07-update-values
-- HQL: update Order o set o.description = null, o.updateAt = :p1, o.active = true where o.id = :p2
update orders o1_0
set description=null,update_at=?,active=1
where o1_0.id=?

-- @example 07-update-add
-- HQL: update Order o set o.cnt = o.cnt + 1 where o.status = 1
update orders o1_0
set cnt=(o1_0.cnt+1)
where o1_0.status=1

-- @example 07-update-embedded-all
-- HQL: update Order o set o.address = :p1 where o.id = :p2
update orders o1_0
set city=?,zip=?
where o1_0.id=?

-- @example 07-update-sum
-- HQL: update Order o set o.total = (select sum(i.price) from Item i where i.order.id = o.id) where o.status = 1
update orders o1_0
set total=(select sum(i1_0.price) from items i1_0 where i1_0.order_id=o1_0.id)
where o1_0.status=1

-- @example 07-update-where-path
-- HQL: update Order o set o.status = 0 where exists (select 1 from Customer c where c.id = o.customer.id and c.grade > 3)
update orders o1_0
set status=0
where exists(select 1 from customers c1_0 where c1_0.id=o1_0.customer_id and c1_0.grade>3)

-- @example 07-update-not-exists
-- HQL: update Order o set o.status = 9 where not exists (select 1 from Item i where i.order.id = o.id)
update orders o1_0
set status=9
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- @example 07-update-versioned
-- HQL: update versioned VOrder o set o.status = 8 where o.id = :p1 and o.version = :p2
update v_orders v1_0
set status=8,version=(v1_0.version+1)
where v1_0.id=? and v1_0.version=?

-- @example 07-delete-not-exists
-- HQL: delete from Order o where not exists (select 1 from Item i where i.order.id = o.id)
delete o1_0 from orders o1_0
where not exists(select 1 from items i1_0 where i1_0.order_id=o1_0.id)

-- @example 07-delete-ids
-- HQL: delete from Item i where i.id in (:p1)
delete i1_0 from items i1_0
where i1_0.id in (?,?,?)

-- @example 07-insert-generated
-- HQL: insert into IdOrder (price, status) values (:p1, 1)
insert into id_orders(price,status)
values (?,1)

-- @example 07-insert-group
-- HQL: insert into Summary (customerId, total, cnt) select o.customer.id, sum(o.price), count(*) from Order o group by o.customer.id
insert into summary(customer_id,total,cnt)
select o1_0.customer_id,sum(o1_0.price),count(*)
from orders o1_0
group by o1_0.customer_id

-- @example 08-function-value-side
-- HQL: select o from Order o where o.price > function('FN_DISCOUNT' as Double, :p1)
select …
from orders o1_0
where o1_0.price>fn_discount(?)

-- @example 08-function-select
-- HQL: select o.id, c1.name, function('FN_DISCOUNT' as Double, o.price) from Order o left join o.customer c1
select o1_0.id,c1_0.name,fn_discount(o1_0.price)
from orders o1_0
left join customers c1_0 on c1_0.id=o1_0.customer_id

-- @example 08-function-update
-- HQL: update Order o set o.price = function('FN_DISCOUNT' as Double, o.price)
update orders o1_0
set price=fn_discount(o1_0.price)
