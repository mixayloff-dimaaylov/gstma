#!/bin/bash

# stdlib

print_help() {
	printf '%s\n' \
		"${_script} -- dump databases for current day" \
		'' \
		'Usage:' \
		'' \
		" ${_script} [-h]" \
		'   -h - print this help and exit'
}

errexit() {
	printf "${_script}: %b" "${1}" >&2
	exit "${2}"
}

# funcs

# $1 -- database
# $2 -- table
# $3 -- dump date, also used for specify path
dump() {
    typeset -r db="${1}" table="${2}" _date="${3}"

    _target_dir="${_dump_path}/$(date +'%Y/%m/%d' -d "${_date}")"
    _target_file="${_target_dir}/${db}.${table}.parquet"
    mkdir -p -- "${_target_dir}"

    echo "export table ${table} from database ${db}"

    # dump
    clickhouse-client --host "${_host}" -q \
"SELECT * FROM ${db}.${table} "\
"WHERE toDate(toDateTime64(\`time\` / 1000, 3, 'UTC')) == toDate('${_date}') "\
"ORDER BY time ASC" \
        --format Parquet > "${_target_file}"

    chmod a-w "${_target_file}"
}

export LANG=en
export TZ=UTC

# defaults
_script="$(basename "${0}")"
_host='clickhouse'
_dump_path='/datadump'
_date="$(date -uI -d 'yesterday')"
_date_to="${_date}"

while getopts ':h' _opt ; do
	case "${_opt}" in
		h)
			print_help
			exit 0
			;;
		*)
			errexit 'No such switch. Exiting...\n' '1'
			;;
	esac
done

shift $((OPTIND-1))

[ "${#}" -gt '2' ] && errexit "Not exact number of arguments.\n" '1'

if [ "${#}" -gt '0' ] ; then
    _date="${1}"
    _date_to="${_date}"
fi

if [ "${#}" -gt '1' ] ; then
    _date_to="${2}"
fi

if [ ! -d "${_dump_path}" ] ; then
	errexit "Target directory ${_dump_path} doesn't exists. Exiting." '1'
fi

while read -r db ; do
	if echo "${db}" | grep -iqP '(system|information_schema|default)' ; then
		printf '%s\n' "skip system db ${db}"
		continue 1
	fi

	while read -r table ; do
		if [[ "${table}" == '.inner.'* ]] ; then
			printf '%s\n' "skip materialized view ${table} (${db})"
			continue
		fi

		if [[ "${table}" == 'sdcb' ]] ; then
			printf '%s\n' "skip table ${table} (${db})"
			continue
		fi

        dump "${db}" "${table}" "${_date}"
	done < <(clickhouse-client --host "${_host}" -q "SHOW TABLES FROM ${db}")
done < <(clickhouse-client --host "${_host}" -q 'SHOW DATABASES')
