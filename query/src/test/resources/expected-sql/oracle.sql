-- SQL that Hibernate 6.6 makes for the manual examples on oracle.
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
where o1_0.active=1
order by o1_0.id
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
where o1_0.active=1
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
