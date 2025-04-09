/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

create table token (
    id                    character(36) NOT NULL,
    provider              character varying NOT NULL,
    access_token          character varying NOT NULL,
    refresh_token         character varying,
    process_instance_id   character(36) NOT NULL
    primary key (id)
);


alter table if exists token
add constraint fk_token_process_instance_id foreign key (process_instance_id) references process_instances(id) on delete cascade;