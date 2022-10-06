#!/bin/bash

# stdlib

errexit() {
	printf "${_script}: %b" "${1}" >&2
	exit "${2}"
}

print_help() {
	printf '%s\n' \
		"${_script} -- dump observations for satellite " \
		"" \
		"Usage:" \
		"" \
		" ${_script} -i [-h] [-n]" \
		" ${_script} [-h] [-n] <sat> <from> <to> <secondaryfreq>" \
		"   -h - print this help and exit" \
		"   -i - input dump parameters in interactive mode" \
		"   -n - don't recreate the dumps folder"
}

# $1 -- sat
# $2 -- from
# $3 -- to
dump_range() {
    for _db in 'rawdata' 'computed' ; do
    if [[ "${1}" =~ ^GPS ]] ; then
        _cols=\
"     anyIf(psr, freq = 'L1CA') AS P1,"\
"     anyIf(psr, freq = 'L2C') AS P2,"\
"     anyIf(psr, freq = 'L5Q') AS P5,"\
"     anyIf(adr, freq = 'L1CA') AS L1,"\
"     anyIf(adr, freq = 'L2C') AS L2,"\
"     anyIf(adr, freq = 'L5Q') AS L5,"
    elif [[ "${1}" =~ ^GLONASS ]] ; then
        _cols=\
"     anyIf(psr, freq = 'L1CA') AS P1,"\
"     anyIf(psr, freq = 'L2CA') AS P2,"\
"     anyIf(psr, freq = 'L2P') AS P2P,"\
"     anyIf(adr, freq = 'L1CA') AS L1,"\
"     anyIf(adr, freq = 'L2CA') AS L2,"\
"     anyIf(adr, freq = 'L2P') AS L2P,"
    else
        errexit "Unsupported system.\n" '1'
    fi

    docker-compose exec "${_cont_name}" 'clickhouse-client' '--query' \
" SELECT"\
"     time,"\
"${_cols}"\
"     sat"\
" FROM"\
"     ${_db}.range"\
" WHERE"\
"     sat='${1}'"\
"     AND time BETWEEN ${2} AND ${3}"\
" GROUP BY"\
"     time, sat"\
" ORDER BY"\
"     time ASC" \
    '--format' 'CSVWithNames' >> "${_dump_path}/${_db}_range_${1}_${2}_${3}.csv"
    done
}

# $1 -- sat
# $2 -- from
# $3 -- to
dump_satxyz2() {
    for _db in 'rawdata' 'computed' ; do
    docker-compose exec "${_cont_name}" 'clickhouse-client' '--query' \
" SELECT"\
"     time,"\
"     elevation,"\
"     sat"\
" FROM"\
"     ${_db}.satxyz2"\
" WHERE"\
"     sat='${1}'"\
"     AND time BETWEEN ${2} AND ${3}"\
" ORDER BY"\
"     time ASC" \
    '--format' 'CSVWithNames' >> "${_dump_path}/${_db}_satxyz2_${1}_${2}_${3}.csv"
    done
}

# $1 -- sat
# $2 -- from
# $3 -- to
# $4 -- secondaryfreq
dump_ismrawtec() {
    for _db in 'rawdata' 'computed' ; do
    docker-compose exec "${_cont_name}" 'clickhouse-client' '--query' \
" SELECT"\
"     time,"\
"     anyIf(tec, secondaryfreq = '${4}') AS TEC,"\
"     sat"\
" FROM"\
"     ${_db}.ismrawtec"\
" WHERE"\
"     sat='${1}'"\
"     AND time BETWEEN ${2} AND ${3}"\
" GROUP BY"\
"     time,"\
"     sat"\
" ORDER BY"\
"     time ASC" \
    '--format' 'CSVWithNames' >> "${_dump_path}/${_db}_ismrawtec_${1}_${2}_${3}_${4}.csv"
    done
}

# $1 -- sat
# $2 -- from
# $3 -- to
# $4 -- secondaryfreq
dump() {
    dump_range "${1}" "${2}" "${3}"
    dump_satxyz2 "${1}" "${2}" "${3}"
    dump_ismrawtec "${1}" "${2}" "${3}" "${4}"
}

LANG=en

# defaults
_script="$(basename "${0}")"
_dump_path='/tmp/rawdump'
_cont_name='clickhouse'

while getopts ':hin' _opt ; do
	case "${_opt}" in
		h)
			print_help
			exit 0
			;;
		i)
			_sw_interactive='true'
			;;
		n)
			_sw_noclobber='true'
			;;
		*)
			errexit 'No such switch. Exiting...\n' '1'
			;;
	esac
done

shift $((OPTIND-1))

if [[ "${_sw_interactive}" == 'true' ]] ; then
	[ "${#}" -ne '0' ] && errexit "Not exact number of arguments.\n" '1'
else
	[ "${#}" -ne '4' ] && errexit "Not exact number of arguments.\n" '1'
fi

if [[ "${_sw_noclobber}" != 'true' ]] ; then
	printf 'Removing old dumps...\n'
	rm -rfv -- "${_dump_path}"
fi

mkdir -p -- "${_dump_path}"

if [[ "${_sw_interactive}" == 'true' ]] ; then
	while true; do
		printf 'Input parameters (or ^D to quit):\n'

		read -ep "sat > "           _sat           &&
		read -ep "from > "          _from          && 
		read -ep "to > "            _to            &&
		read -ep "secondaryfreq > " _secondaryfreq || \
			{ [[ "${?}" == 1 ]] && { printf 'Input canceled. Exiting.\n' ; break ;} ;
			  [[ "${?}" != 0 ]] && { break ;} ;}

		[[ -z "${_sat}" || -z "${_from}" || -z "${_to}" || -z "${_secondaryfreq}" ]] \
			&& { printf 'Value cannot be empty\n' ; continue ;}

		dump "${_sat}" "${_from}" "${_to}" "${_secondaryfreq}"

		unset _sat _from _to _secondaryfreq
	done
else
	dump "${1}" "${2}" "${3}" "${4}"
fi

printf '\nTotal:\n'
du --human-readable -- "${_dump_path}"/*.csv
